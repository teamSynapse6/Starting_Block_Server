package com.startingblock.global.infrastructure.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.global.infrastructure.feign.dto.PdfResultRes;
import com.startingblock.global.infrastructure.feign.dto.PdfUploadRes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PdfClient {

    private final ObjectMapper objectMapper;

    @Value("${ai-rag.cli.python-executable:python3}")
    private String pythonExecutable;

    @Value("${ai-rag.cli.working-directory:ai-rag}")
    private String workingDirectory;

    @Value("${ai-rag.cli.timeout-seconds:600}")
    private long timeoutSeconds;

    public PdfUploadRes uploadPdf(final List<PdfUploadReq> pdfUploadReq) {
        try {
            String input = objectMapper.writeValueAsString(pdfUploadReq);
            String output = execute(List.of("upload"), input);
            return objectMapper.readValue(output, PdfUploadRes.class);
        } catch (IOException exception) {
            throw new IllegalStateException("AI/RAG upload command failed", exception);
        }
    }

    public PdfResultRes getUploadPdfResult() {
        try {
            String output = execute(List.of("validation"), null);
            return objectMapper.readValue(output, PdfResultRes.class);
        } catch (IOException exception) {
            throw new IllegalStateException("AI/RAG validation command failed", exception);
        }
    }

    private String execute(final List<String> arguments, final String stdin) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(buildCommand(arguments));
        processBuilder.directory(new java.io.File(workingDirectory));
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        if (stdin != null) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(stdin);
            }
        } else {
            process.getOutputStream().close();
        }

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
            output = builder.toString().trim();
        }

        boolean finished;
        try {
            finished = process.waitFor(Duration.ofSeconds(timeoutSeconds).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI/RAG command interrupted", exception);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("AI/RAG command timed out: " + arguments);
        }

        if (process.exitValue() != 0) {
            throw new IllegalStateException("AI/RAG command failed: " + arguments + System.lineSeparator() + output);
        }

        return output;
    }

    private List<String> buildCommand(final List<String> arguments) {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(pythonExecutable);
        command.add("-m");
        command.add("app.cli");
        command.addAll(arguments);
        return command;
    }
}
