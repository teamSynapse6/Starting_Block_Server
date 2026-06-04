package com.startingblock.global.infrastructure.airag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiRagGpuConcurrencyLimiter {

    @Value("${ai-rag.llm.dynamic-concurrency.enabled:true}")
    private boolean enabled;

    @Value("${ai-rag.llm.dynamic-concurrency.max-concurrency:0}")
    private int maxConcurrency;

    @Value("${ai-rag.llm.dynamic-concurrency.request-memory-mb:4096}")
    private int requestMemoryMb;

    @Value("${ai-rag.llm.dynamic-concurrency.min-free-memory-mb:2048}")
    private int minFreeMemoryMb;

    @Value("${ai-rag.llm.dynamic-concurrency.max-gpu-utilization:92}")
    private int maxGpuUtilization;

    @Value("${ai-rag.llm.dynamic-concurrency.max-memory-usage-percent:88}")
    private int maxMemoryUsagePercent;

    @Value("${ai-rag.llm.dynamic-concurrency.wait-interval-ms:1000}")
    private long waitIntervalMs;

    @Value("${ai-rag.llm.dynamic-concurrency.wait-timeout-seconds:600}")
    private long waitTimeoutSeconds;

    private final AtomicInteger running = new AtomicInteger(0);
    private final AtomicInteger waiting = new AtomicInteger(0);

    public void enterQueue() {
        waiting.incrementAndGet();
    }

    public void leaveQueue() {
        waiting.updateAndGet(value -> Math.max(0, value - 1));
    }

    public Lease tryAcquire() {
        try {
            Capacity capacity = capacity();
            int current = running.get();
            if (current >= capacity.allowedConcurrency()) {
                return null;
            }
            if (running.compareAndSet(current, current + 1)) {
                return new Lease(capacity);
            }
            return null;
        } catch (RuntimeException exception) {
            log.warn("AI/RAG dynamic concurrency acquire failed", exception);
            return null;
        }
    }

    public long waitIntervalMs() {
        return waitIntervalMs;
    }

    public long waitTimeoutSeconds() {
        return waitTimeoutSeconds;
    }

    public String toStatusJson(Capacity capacity, long waitedSeconds) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"stage\":\"dynamic_queue_waiting\",");
        builder.append("\"waited_seconds\":").append(waitedSeconds).append(",");
        builder.append("\"allowed_concurrency\":").append(capacity.allowedConcurrency()).append(",");
        builder.append("\"running\":").append(capacity.running()).append(",");
        builder.append("\"waiting\":").append(capacity.waiting()).append(",");
        builder.append("\"reason\":\"").append(capacity.reason()).append("\"");
        if (capacity.gpu() != null) {
            builder.append(",");
            builder.append("\"gpu\":{");
            builder.append("\"free_memory_mb\":").append(capacity.gpu().totalFreeMemoryMb()).append(",");
            builder.append("\"total_memory_mb\":").append(capacity.gpu().totalMemoryMb()).append(",");
            builder.append("\"used_memory_percent\":").append(capacity.gpu().usedMemoryPercent()).append(",");
            builder.append("\"avg_utilization\":").append(capacity.gpu().averageUtilization()).append(",");
            builder.append("\"max_utilization\":").append(capacity.gpu().maxUtilization()).append(",");
            builder.append("\"gpu_count\":").append(capacity.gpu().gpus().size());
            builder.append("}");
        }
        builder.append("}");
        return builder.toString();
    }

    public String acquiredStatusJson(Lease lease, long waitedSeconds) {
        Capacity capacity = lease.acquiredCapacity();
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"stage\":\"dynamic_slot_acquired\",");
        builder.append("\"waited_seconds\":").append(waitedSeconds).append(",");
        builder.append("\"allowed_concurrency\":").append(capacity.allowedConcurrency()).append(",");
        builder.append("\"running\":").append(running.get()).append(",");
        builder.append("\"waiting\":").append(waiting.get()).append(",");
        builder.append("\"reason\":\"").append(capacity.reason()).append("\"");
        if (capacity.gpu() != null) {
            builder.append(",");
            builder.append("\"gpu\":{");
            builder.append("\"free_memory_mb\":").append(capacity.gpu().totalFreeMemoryMb()).append(",");
            builder.append("\"total_memory_mb\":").append(capacity.gpu().totalMemoryMb()).append(",");
            builder.append("\"used_memory_percent\":").append(capacity.gpu().usedMemoryPercent()).append(",");
            builder.append("\"avg_utilization\":").append(capacity.gpu().averageUtilization()).append(",");
            builder.append("\"max_utilization\":").append(capacity.gpu().maxUtilization()).append(",");
            builder.append("\"gpu_count\":").append(capacity.gpu().gpus().size());
            builder.append("}");
        }
        builder.append("}");
        return builder.toString();
    }

    public Capacity snapshot() {
        return capacity();
    }

    public int queueLength() {
        return waiting.get();
    }

    public int runningCount() {
        return running.get();
    }

    private Capacity capacity() {
        GpuStats stats = readGpuStats();
        if (!enabled) {
            return new Capacity(1, running.get(), waiting.get(), stats, "disabled");
        }

        int configuredMax = maxConcurrency <= 0 ? Integer.MAX_VALUE : maxConcurrency;
        if (stats == null) {
            return new Capacity(1, running.get(), waiting.get(), null, "gpu_stats_unavailable");
        }

        if (stats.averageUtilization() >= maxGpuUtilization) {
            return new Capacity(0, running.get(), waiting.get(), stats, "gpu_utilization_high");
        }
        if (stats.usedMemoryPercent() >= maxMemoryUsagePercent) {
            return new Capacity(0, running.get(), waiting.get(), stats, "gpu_memory_high");
        }

        int reservedMemoryMb = Math.max(0, minFreeMemoryMb) * stats.gpus().size();
        int usableFreeMemoryMb = Math.max(0, stats.totalFreeMemoryMb() - reservedMemoryMb);
        int memoryBased = usableFreeMemoryMb / Math.max(1, requestMemoryMb);
        if (memoryBased <= 0) {
            return new Capacity(0, running.get(), waiting.get(), stats, "gpu_busy");
        }

        int allowed = Math.max(1, Math.min(configuredMax, memoryBased));
        return new Capacity(allowed, running.get(), waiting.get(), stats, "ok");
    }

    private GpuStats readGpuStats() {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "nvidia-smi",
                    "--query-gpu=utilization.gpu,memory.free,memory.total",
                    "--format=csv,noheader,nounits"
            ).start();

            boolean finished = process.waitFor(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }

            List<GpuSnapshot> gpus = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length < 3) {
                        continue;
                    }
                    int utilization = Integer.parseInt(parts[0].trim());
                    int freeMemoryMb = Integer.parseInt(parts[1].trim());
                    int totalMemoryMb = Integer.parseInt(parts[2].trim());
                    gpus.add(new GpuSnapshot(utilization, freeMemoryMb, totalMemoryMb));
                }
            }
            if (gpus.isEmpty()) {
                return null;
            }

            int totalFree = gpus.stream().mapToInt(GpuSnapshot::freeMemoryMb).sum();
            int totalMemory = gpus.stream().mapToInt(GpuSnapshot::totalMemoryMb).sum();
            int maxUtil = gpus.stream().mapToInt(GpuSnapshot::utilization).max().orElse(0);
            int averageUtil = (int) Math.round(gpus.stream().mapToInt(GpuSnapshot::utilization).average().orElse(0));
            int usedMemoryPercent = totalMemory <= 0 ? 100 : (int) Math.round(((double) (totalMemory - totalFree) / totalMemory) * 100);
            return new GpuStats(totalFree, totalMemory, usedMemoryPercent, averageUtil, maxUtil, gpus);
        } catch (Exception exception) {
            log.debug("AI/RAG GPU stats unavailable", exception);
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    public record Lease(Capacity acquiredCapacity) {
    }

    public void release() {
        running.updateAndGet(value -> Math.max(0, value - 1));
    }

    public record Capacity(
            int allowedConcurrency,
            int running,
            int waiting,
            GpuStats gpu,
            String reason
    ) {
    }

    public record GpuStats(
            int totalFreeMemoryMb,
            int totalMemoryMb,
            int usedMemoryPercent,
            int averageUtilization,
            int maxUtilization,
            List<GpuSnapshot> gpus
    ) {
    }

    public record GpuSnapshot(int utilization, int freeMemoryMb, int totalMemoryMb) {
    }
}
