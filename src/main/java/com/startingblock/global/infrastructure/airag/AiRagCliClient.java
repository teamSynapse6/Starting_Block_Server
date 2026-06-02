package com.startingblock.global.infrastructure.airag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiRagCliClient {

    private final ObjectMapper objectMapper;

    @Value("${ai-rag.cli.python-executable:python3}")
    private String pythonExecutable;

    @Value("${ai-rag.cli.working-directory:ai-rag}")
    private String workingDirectory;

    @Value("${ai-rag.cli.timeout-seconds:600}")
    private long timeoutSeconds;

    @Value("${ai-rag.worker.enabled:true}")
    private boolean workerEnabled;

    private final Object workerMonitor = new Object();
    private final Semaphore workerSemaphore = new Semaphore(1);
    private final Map<String, BlockingQueue<Map<String, Object>>> workerResponses = new ConcurrentHashMap<>();
    private Process workerProcess;
    private BufferedWriter workerWriter;

    public String execute(final List<String> arguments, final String stdin) throws IOException {
        if (workerEnabled) {
            return executeWithWorker(arguments, stdin);
        }
        return executeWithProcess(arguments, stdin);
    }

    private String executeWithWorker(final List<String> arguments, final String stdin) throws IOException {
        long startedAt = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        workerResponses.put(requestId, queue);

        try {
            acquireWorker();
            ensureWorkerRunning();
            log.info("AI/RAG worker start command={}", arguments);
            writeWorkerRequest(requestId, arguments, stdin, false);

            Map<String, Object> frame = pollFrame(queue, arguments, startedAt);
            int exit = ((Number) frame.getOrDefault("exit", 1)).intValue();
            String stdout = String.valueOf(frame.getOrDefault("stdout", "")).trim();
            String stderr = String.valueOf(frame.getOrDefault("stderr", "")).trim();

            if (exit != 0) {
                log.warn("AI/RAG worker failed command={} exit={} elapsed_ms={} stderr={}",
                        arguments, exit, elapsedMillis(startedAt), abbreviate(stderr));
                throw new IllegalStateException("AI/RAG worker command failed: " + arguments + System.lineSeparator() + stderr);
            }

            if (!stderr.isBlank()) {
                log.warn("AI/RAG worker stderr command={} elapsed_ms={} stderr={}", arguments, elapsedMillis(startedAt), abbreviate(stderr));
            }
            log.info("AI/RAG worker success command={} elapsed_ms={} stdout_chars={}", arguments, elapsedMillis(startedAt), stdout.length());
            return stdout;
        } finally {
            workerResponses.remove(requestId);
            workerSemaphore.release();
        }
    }

    private String executeWithProcess(final List<String> arguments, final String stdin) throws IOException {
        long startedAt = System.nanoTime();
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(buildCommand(arguments));
        processBuilder.directory(new File(workingDirectory));

        log.info("AI/RAG CLI start command={}", arguments);
        Process process = processBuilder.start();
        writeStdin(process, stdin);

        CompletableFuture<String> stdoutFuture = readAsync(process, true);
        CompletableFuture<String> stderrFuture = readAsync(process, false);

        boolean finished;
        try {
            finished = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI/RAG command interrupted", exception);
        }

        if (!finished) {
            process.destroyForcibly();
            log.warn("AI/RAG CLI timeout command={} elapsed_ms={}", arguments, elapsedMillis(startedAt));
            throw new IllegalStateException("AI/RAG command timed out: " + arguments);
        }

        String stdout = stdoutFuture.join().trim();
        String stderr = stderrFuture.join().trim();
        if (process.exitValue() != 0) {
            log.warn("AI/RAG CLI failed command={} exit={} elapsed_ms={} stderr={}",
                    arguments, process.exitValue(), elapsedMillis(startedAt), abbreviate(stderr));
            throw new IllegalStateException("AI/RAG command failed: " + arguments + System.lineSeparator() + stderr);
        }

        if (!stderr.isBlank()) {
            log.warn("AI/RAG CLI stderr command={} elapsed_ms={} stderr={}", arguments, elapsedMillis(startedAt), abbreviate(stderr));
        }
        log.info("AI/RAG CLI success command={} elapsed_ms={} stdout_chars={}", arguments, elapsedMillis(startedAt), stdout.length());
        return stdout;
    }

    public RunningCommand startStreaming(final List<String> arguments, final String stdin) throws IOException {
        if (workerEnabled) {
            return startStreamingWithWorker(arguments, stdin);
        }
        return startStreamingWithProcess(arguments, stdin);
    }

    private RunningCommand startStreamingWithWorker(final List<String> arguments, final String stdin) throws IOException {
        long startedAt = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        workerResponses.put(requestId, queue);

        acquireWorker();
        try {
            ensureWorkerRunning();
            log.info("AI/RAG worker stream start command={}", arguments);
            writeWorkerRequest(requestId, arguments, stdin, true);
            return new RunningCommand(null, null, Duration.ofSeconds(timeoutSeconds), startedAt, true, requestId, queue);
        } catch (IOException | RuntimeException exception) {
            workerResponses.remove(requestId);
            workerSemaphore.release();
            throw exception;
        }
    }

    private RunningCommand startStreamingWithProcess(final List<String> arguments, final String stdin) throws IOException {
        long startedAt = System.nanoTime();
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(buildCommand(arguments));
        processBuilder.directory(new File(workingDirectory));

        log.info("AI/RAG CLI stream start command={}", arguments);
        Process process = processBuilder.start();
        writeStdin(process, stdin);
        CompletableFuture<String> stderrFuture = readAsync(process, false);
        return new RunningCommand(process, stderrFuture, Duration.ofSeconds(timeoutSeconds), startedAt, false, null, null);
    }

    private void writeStdin(final Process process, final String stdin) throws IOException {
        if (stdin != null) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(stdin);
            }
        } else {
            process.getOutputStream().close();
        }
    }

    private CompletableFuture<String> readAsync(final Process process, final boolean stdout) {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stdout ? process.getInputStream() : process.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append(System.lineSeparator());
                }
                return builder.toString();
            } catch (IOException exception) {
                throw new IllegalStateException("AI/RAG command output read failed", exception);
            }
        });
    }

    private List<String> buildCommand(final List<String> arguments) {
        ArrayList<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("-m");
        command.add("app.cli");
        command.addAll(arguments);
        return command;
    }

    private List<String> buildWorkerCommand() {
        ArrayList<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add("-m");
        command.add("app.worker");
        return command;
    }

    private void acquireWorker() {
        try {
            if (!workerSemaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("AI/RAG worker is busy");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI/RAG worker acquire interrupted", exception);
        }
    }

    private void ensureWorkerRunning() throws IOException {
        synchronized (workerMonitor) {
            if (workerProcess != null && workerProcess.isAlive() && workerWriter != null) {
                return;
            }

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(buildWorkerCommand());
            processBuilder.directory(new File(workingDirectory));

            workerProcess = processBuilder.start();
            workerWriter = new BufferedWriter(new OutputStreamWriter(workerProcess.getOutputStream(), StandardCharsets.UTF_8));
            startWorkerStdoutReader(workerProcess);
            startWorkerStderrReader(workerProcess);
            log.info("AI/RAG worker process started");
        }
    }

    private void writeWorkerRequest(final String requestId, final List<String> arguments, final String stdin, final boolean stream) throws IOException {
        Map<String, Object> request = new HashMap<>();
        request.put("id", requestId);
        request.put("command", arguments);
        request.put("stdin", stdin == null ? "" : stdin);
        request.put("stream", stream);

        synchronized (workerMonitor) {
            workerWriter.write(objectMapper.writeValueAsString(request));
            workerWriter.newLine();
            workerWriter.flush();
        }
    }

    private Map<String, Object> pollFrame(final BlockingQueue<Map<String, Object>> queue, final List<String> arguments, final long startedAt) {
        try {
            Map<String, Object> frame = queue.poll(timeoutSeconds, TimeUnit.SECONDS);
            if (frame == null) {
                restartWorker();
                log.warn("AI/RAG worker timeout command={} elapsed_ms={}", arguments, elapsedMillis(startedAt));
                throw new IllegalStateException("AI/RAG worker command timed out: " + arguments);
            }
            return frame;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI/RAG worker command interrupted", exception);
        }
    }

    private void startWorkerStdoutReader(final Process process) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Map<String, Object> frame = objectMapper.readValue(line, new TypeReference<>() {
                    });
                    String requestId = String.valueOf(frame.getOrDefault("id", ""));
                    BlockingQueue<Map<String, Object>> queue = workerResponses.get(requestId);
                    if (queue != null) {
                        queue.offer(frame);
                    } else {
                        log.warn("AI/RAG worker frame without waiter id={} type={}", requestId, frame.get("type"));
                    }
                }
            } catch (Exception exception) {
                log.warn("AI/RAG worker stdout reader stopped", exception);
            }
        });
    }

    private void startWorkerStderrReader(final Process process) {
        CompletableFuture.runAsync(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.warn("AI/RAG worker stderr: {}", line);
                }
            } catch (IOException exception) {
                log.warn("AI/RAG worker stderr reader stopped", exception);
            }
        });
    }

    public void finishWorkerStream(final RunningCommand command) {
        if (command.workerStream()) {
            workerResponses.remove(command.requestId());
            workerSemaphore.release();
        }
    }

    private void restartWorker() {
        synchronized (workerMonitor) {
            if (workerProcess != null) {
                workerProcess.destroyForcibly();
            }
            workerProcess = null;
            workerWriter = null;
        }
    }

    public long elapsedMillis(final long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    public String abbreviate(final String value) {
        if (value == null || value.length() <= 4000) {
            return value;
        }
        return value.substring(0, 4000) + "...";
    }

    public record RunningCommand(
            Process process,
            CompletableFuture<String> stderrFuture,
            Duration timeout,
            long startedAt,
            boolean workerStream,
            String requestId,
            BlockingQueue<Map<String, Object>> frameQueue
    ) {
    }
}
