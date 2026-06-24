package com.startingblock.global.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.domain.notification.application.NotificationService;
import com.startingblock.global.config.security.token.CurrentUser;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.infrastructure.airag.AiRagCliClient;
import com.startingblock.global.infrastructure.airag.AiRagGpuConcurrencyLimiter;
import com.startingblock.global.infrastructure.airag.LlmConversationQueryRepository;
import com.startingblock.global.infrastructure.airag.LlmConversationSummaryRes;
import com.startingblock.global.error.DefaultAuthenticationException;
import com.startingblock.global.payload.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Tag(name = "AI/RAG Legacy API", description = "기존 PDFLLM FastAPI 라우트 호환 API")
@RestController
@RequiredArgsConstructor
@Slf4j
public class AiRagLegacyController {

    private final AiRagCliClient aiRagCliClient;
    private final AiRagGpuConcurrencyLimiter gpuConcurrencyLimiter;
    private final LlmConversationQueryRepository llmConversationQueryRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Map<String, AiRagCliClient.RunningCommand> activeChatCommands = new ConcurrentHashMap<>();
    private final Set<String> cancelRequestedThreads = ConcurrentHashMap.newKeySet();

    @Operation(summary = "저장된 파일 리스트 반환")
    @GetMapping("/validation")
    public ResponseEntity<String> validateFiles() throws IOException {
        return json(aiRagCliClient.execute(List.of("validation"), null));
    }

    @Operation(summary = "특정 공고 전처리 텍스트 조회")
    @GetMapping(value = "/announcement", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAnnouncement(@RequestParam("id") final String id) throws IOException {
        String text = aiRagCliClient.execute(List.of("get-announcement", "--id", id), null);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain;charset=UTF-8"))
                .body(text);
    }

    @Operation(summary = "저장된 공고 파일 및 벡터 삭제")
    @DeleteMapping(value = "/announcement/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteAnnouncement(@RequestBody final AnnouncementDeleteRequest request) throws IOException {
        String stdin = objectMapper.writeValueAsString(request);
        return json(aiRagCliClient.execute(List.of("delete-announcement"), stdin));
    }

    @Operation(summary = "공고 파일 업로드 및 임베딩")
    @PostMapping(value = "/announcement/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadAnnouncement(@RequestBody final List<PdfUploadReq> request) throws IOException {
        String stdin = objectMapper.writeValueAsString(request);
        return json(aiRagCliClient.execute(List.of("upload"), stdin));
    }

    @Operation(summary = "대화 UUID 생성")
    @PostMapping(value = "/llm/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> startConversation(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) throws IOException {
        UserPrincipal currentUser = requireUser(userPrincipal);
        return json(aiRagCliClient.execute(List.of("llm-start", "--user-id", String.valueOf(currentUser.getId())), null));
    }

    @Operation(summary = "사용자 활성 대화 목록 조회")
    @GetMapping(value = "/llm/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<LlmConversationSummaryRes>> listConversations(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal
    ) {
        UserPrincipal currentUser = requireUser(userPrincipal);
        return ResponseEntity.ok(llmConversationQueryRepository.findActiveConversations(currentUser.getId()));
    }

    @Operation(summary = "RAG 기반 LLM 채팅")
    @PostMapping(value = "/llm/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chat(@RequestBody final LlmChatRequest request) throws IOException {
        String stdin = objectMapper.writeValueAsString(request);
        cancelRequestedThreads.remove(request.thread_id());
        try {
            aiRagCliClient.executeProcessOnly(List.of("llm-mark-queued"), stdin);
        } catch (Exception exception) {
            log.warn("AI/RAG Redis queue status mark failed thread_id={}", request.thread_id(), exception);
        }
        StreamingResponseBody body = outputStream -> {
            AiRagGpuConcurrencyLimiter.Lease lease = null;
            AiRagCliClient.RunningCommand command = null;
            long waitedSeconds = 0;
            boolean clientConnected = true;
            gpuConcurrencyLimiter.enterQueue();
            try {
                long startedWaitingAt = System.nanoTime();
                while (lease == null) {
                    if (isCancelRequested(request.thread_id())) {
                        markCancelled(request.thread_id(), "user_cancelled");
                        cancelRequestedThreads.remove(request.thread_id());
                        if (clientConnected) {
                            writeSse(outputStream, "status", new CancelStatus("cancelled", "user_cancelled"));
                        }
                        return;
                    }
                    lease = gpuConcurrencyLimiter.tryAcquire();
                    if (lease != null) {
                        break;
                    }
                    if (clientConnected) {
                        try {
                            writeRawSse(outputStream, "status", gpuConcurrencyLimiter.toStatusJson(gpuConcurrencyLimiter.snapshot(), waitedSeconds));
                        } catch (IOException exception) {
                            clientConnected = false;
                            log.info("AI/RAG SSE client disconnected while queued; generation will continue thread_id={}", request.thread_id());
                        }
                    }
                    try {
                        Thread.sleep(gpuConcurrencyLimiter.waitIntervalMs());
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        if (clientConnected) {
                            writeSseError(outputStream, "대기열 처리가 중단되었습니다.");
                        }
                        return;
                    }
                    waitedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedWaitingAt);
                    if (waitedSeconds >= gpuConcurrencyLimiter.waitTimeoutSeconds()) {
                        if (clientConnected) {
                            writeSseError(outputStream, "GPU 대기열 처리 시간이 초과되었습니다.");
                        }
                        return;
                    }
                }
            } finally {
                gpuConcurrencyLimiter.leaveQueue();
            }

            try {
                if (isCancelRequested(request.thread_id())) {
                    markCancelled(request.thread_id(), "user_cancelled");
                    cancelRequestedThreads.remove(request.thread_id());
                    if (clientConnected) {
                        writeSse(outputStream, "status", new CancelStatus("cancelled", "user_cancelled"));
                    }
                    return;
                }
                if (clientConnected) {
                    try {
                        writeRawSse(outputStream, "status", gpuConcurrencyLimiter.acquiredStatusJson(lease, waitedSeconds));
                    } catch (IOException exception) {
                        clientConnected = false;
                        log.info("AI/RAG SSE client disconnected before worker start; generation continues thread_id={}", request.thread_id());
                    }
                }
                command = aiRagCliClient.startStreamingProcessOnly(List.of("llm-chat"), stdin);
                activeChatCommands.put(request.thread_id(), command);

                try (InputStream inputStream = command.process().getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        if (clientConnected) {
                            try {
                                outputStream.write(buffer, 0, read);
                                outputStream.flush();
                            } catch (IOException exception) {
                                clientConnected = false;
                                log.info("AI/RAG SSE client disconnected; generation continues thread_id={} elapsed_ms={}",
                                        request.thread_id(), aiRagCliClient.elapsedMillis(command.startedAt()));
                            }
                        }
                    }
                }

                boolean finished;
                try {
                    finished = command.process().waitFor(command.timeout().toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    command.process().destroyForcibly();
                    log.warn("AI/RAG CLI stream interrupted command={} elapsed_ms={}", List.of("llm-chat"), aiRagCliClient.elapsedMillis(command.startedAt()));
                    writeSseError(outputStream, "채팅 처리가 중단되었습니다.");
                    return;
                }

                if (!finished) {
                    command.process().destroyForcibly();
                    log.warn("AI/RAG CLI stream timeout command={} elapsed_ms={}", List.of("llm-chat"), aiRagCliClient.elapsedMillis(command.startedAt()));
                    writeSseError(outputStream, "채팅 처리 시간이 초과되었습니다.");
                    return;
                }

                String stderr = command.stderrFuture().join().trim();
                if (!stderr.isBlank()) {
                    log.warn("AI/RAG CLI stream stderr command={} elapsed_ms={} stderr={}",
                            List.of("llm-chat"), aiRagCliClient.elapsedMillis(command.startedAt()), aiRagCliClient.abbreviate(stderr));
                }

                if (command.process().exitValue() != 0) {
                    log.warn("AI/RAG CLI stream failed command={} exit={} elapsed_ms={}",
                            List.of("llm-chat"), command.process().exitValue(), aiRagCliClient.elapsedMillis(command.startedAt()));
                } else {
                    log.info("AI/RAG CLI stream success command={} elapsed_ms={}",
                            List.of("llm-chat"), aiRagCliClient.elapsedMillis(command.startedAt()));
                    notificationService.sendLlmComplete(request.thread_id());
                }
            } finally {
                activeChatCommands.remove(request.thread_id());
                gpuConcurrencyLimiter.release();
            }
        };

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    @Operation(summary = "온디바이스 LLM 답변 생성을 위한 RAG 검색")
    @PostMapping(value = "/llm/retrieval", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> retrieval(@RequestBody final LlmChatRequest request) throws IOException {
        String stdin = objectMapper.writeValueAsString(request);
        StreamingResponseBody body = outputStream -> {
            AiRagCliClient.RunningCommand command = null;
            boolean clientConnected = true;
            try {
                command = aiRagCliClient.startStreamingProcessOnly(List.of("llm-retrieval"), stdin);

                try (InputStream inputStream = command.process().getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        if (clientConnected) {
                            try {
                                outputStream.write(buffer, 0, read);
                                outputStream.flush();
                            } catch (IOException exception) {
                                clientConnected = false;
                                log.info("AI/RAG retrieval SSE client disconnected; retrieval continues thread_id={} elapsed_ms={}",
                                        request.thread_id(), aiRagCliClient.elapsedMillis(command.startedAt()));
                            }
                        }
                    }
                }

                boolean finished;
                try {
                    finished = command.process().waitFor(command.timeout().toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    command.process().destroyForcibly();
                    log.warn("AI/RAG retrieval stream interrupted command={} elapsed_ms={}", List.of("llm-retrieval"), aiRagCliClient.elapsedMillis(command.startedAt()));
                    if (clientConnected) {
                        writeSseError(outputStream, "검색 처리가 중단되었습니다.");
                    }
                    return;
                }

                if (!finished) {
                    command.process().destroyForcibly();
                    log.warn("AI/RAG retrieval stream timeout command={} elapsed_ms={}", List.of("llm-retrieval"), aiRagCliClient.elapsedMillis(command.startedAt()));
                    if (clientConnected) {
                        writeSseError(outputStream, "검색 처리 시간이 초과되었습니다.");
                    }
                    return;
                }

                String stderr = command.stderrFuture().join().trim();
                if (!stderr.isBlank()) {
                    log.warn("AI/RAG retrieval stream stderr command={} elapsed_ms={} stderr={}",
                            List.of("llm-retrieval"), aiRagCliClient.elapsedMillis(command.startedAt()), aiRagCliClient.abbreviate(stderr));
                }

                if (command.process().exitValue() != 0) {
                    log.warn("AI/RAG retrieval stream failed command={} exit={} elapsed_ms={}",
                            List.of("llm-retrieval"), command.process().exitValue(), aiRagCliClient.elapsedMillis(command.startedAt()));
                } else {
                    log.info("AI/RAG retrieval stream success command={} elapsed_ms={}",
                            List.of("llm-retrieval"), aiRagCliClient.elapsedMillis(command.startedAt()));
                }
            } finally {
                if (command != null) {
                    activeChatCommands.remove(request.thread_id());
                }
            }
        };

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    @Operation(summary = "온디바이스 LLM 생성 답변 저장")
    @PostMapping(value = "/llm/reply-save", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> saveReply(@RequestBody final LlmReplySaveRequest request) throws IOException {
        String stdin = objectMapper.writeValueAsString(request);
        String response = aiRagCliClient.execute(List.of("llm-reply-save"), stdin);
        JsonNode root = objectMapper.readTree(response);
        if (root.path("saved").asBoolean(false)) {
            notificationService.sendLlmComplete(request.thread_id());
        }
        return json(response);
    }

    @Operation(summary = "RAG 기반 LLM 채팅 진행 상태 조회")
    @GetMapping(value = "/llm/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> conversationStatus(@RequestParam("thread_id") final String threadId) throws IOException {
        return json(aiRagCliClient.execute(List.of("llm-status", "--thread-id", threadId), null));
    }

    @Operation(summary = "RAG 기반 LLM 채팅 SSE 재연결")
    @GetMapping(value = "/llm/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> conversationStream(
            @RequestParam("thread_id") final String threadId,
            @RequestParam(value = "after_seq", defaultValue = "0") final long afterSeq
    ) {
        StreamingResponseBody body = outputStream -> {
            long currentSeq = Math.max(afterSeq, 0);
            long idleLoops = 0;
            while (true) {
                String raw;
                try {
                    raw = aiRagCliClient.execute(List.of("llm-events", "--thread-id", threadId, "--after-seq", String.valueOf(currentSeq)), null);
                } catch (Exception exception) {
                    log.warn("AI/RAG SSE replay failed thread_id={}", threadId, exception);
                    writeSseError(outputStream, "SSE 이벤트를 조회하는 동안 오류가 발생했습니다.");
                    return;
                }

                JsonNode root = objectMapper.readTree(raw);
                JsonNode events = root.path("events");
                boolean wrote = false;
                if (events.isArray()) {
                    for (JsonNode eventNode : events) {
                        long seq = eventNode.path("seq").asLong(currentSeq);
                        String event = eventNode.path("event").asText("status");
                        JsonNode data = eventNode.path("data");
                        try {
                            writeRawSse(outputStream, event, objectMapper.writeValueAsString(data));
                        } catch (IOException exception) {
                            log.info("AI/RAG replay SSE client disconnected thread_id={} last_seq={}", threadId, currentSeq);
                            return;
                        }
                        currentSeq = Math.max(currentSeq, seq);
                        wrote = true;
                    }
                }

                JsonNode generation = root.path("generation");
                String status = generation.path("status").asText("");
                if (Set.of("completed", "failed", "cancelled").contains(status)) {
                    return;
                }
                if (generation.isMissingNode() || generation.isNull()) {
                    writeSse(outputStream, "status", new StreamStatus("not_running", currentSeq));
                    return;
                }

                idleLoops = wrote ? 0 : idleLoops + 1;
                if (idleLoops % 20 == 0) {
                    try {
                        writeSse(outputStream, "status", new StreamStatus("stream_waiting", currentSeq));
                    } catch (IOException exception) {
                        log.info("AI/RAG replay SSE client disconnected thread_id={} last_seq={}", threadId, currentSeq);
                        return;
                    }
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    writeSseError(outputStream, "SSE 재연결 처리가 중단되었습니다.");
                    return;
                }
            }
        };

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    @Operation(summary = "대화 기록 조회")
    @GetMapping(value = "/llm/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> conversationHistory(@RequestParam("thread_id") final String threadId) throws IOException {
        return json(aiRagCliClient.execute(List.of("llm-history", "--thread-id", threadId), null));
    }

    @Operation(summary = "RAG 기반 LLM 채팅 취소")
    @PostMapping(value = "/llm/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancelConversation(@RequestParam("thread_id") final String threadId) throws IOException {
        cancelRequestedThreads.add(threadId);
        AiRagCliClient.RunningCommand command = activeChatCommands.get(threadId);
        if (command != null) {
            destroyCommand(command);
        }
        return json(markCancelled(threadId, "user_cancelled"));
    }

    private void streamWorkerFrames(final AiRagCliClient.RunningCommand command, final java.io.OutputStream outputStream) throws IOException {
        boolean workerStarted = false;
        try {
            long waitedSeconds = 0;
            while (!aiRagCliClient.tryBeginWorkerStream(command)) {
                writeSse(
                        outputStream,
                        "status",
                        new QueueStatus("queue_waiting", aiRagCliClient.workerQueueLength() + 1, waitedSeconds)
                );
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    writeSseError(outputStream, "대기열 처리가 중단되었습니다.");
                    return;
                }
                waitedSeconds += 1;
            }
            workerStarted = true;
            writeSse(outputStream, "status", new QueueStatus("worker_started", 0, waitedSeconds));

            while (true) {
                Map<String, Object> frame = command.frameQueue().poll(command.timeout().toMillis(), TimeUnit.MILLISECONDS);
                if (frame == null) {
                    log.warn("AI/RAG worker stream timeout command={} elapsed_ms={}", List.of("llm-chat"), aiRagCliClient.elapsedMillis(command.startedAt()));
                    writeSseError(outputStream, "채팅 처리 시간이 초과되었습니다.");
                    return;
                }

                String type = String.valueOf(frame.getOrDefault("type", ""));
                if ("stdout".equals(type)) {
                    String data = String.valueOf(frame.getOrDefault("data", ""));
                    outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    continue;
                }

                if ("end".equals(type)) {
                    int exit = ((Number) frame.getOrDefault("exit", 1)).intValue();
                    String stderr = String.valueOf(frame.getOrDefault("stderr", "")).trim();
                    if (!stderr.isBlank()) {
                        log.warn("AI/RAG worker stream stderr command={} elapsed_ms={} stderr={}",
                                List.of("llm-chat"), aiRagCliClient.elapsedMillis(command.startedAt()), aiRagCliClient.abbreviate(stderr));
                    }
                    if (exit != 0) {
                        log.warn("AI/RAG worker stream failed command={} exit={} elapsed_ms={}",
                                List.of("llm-chat"), exit, aiRagCliClient.elapsedMillis(command.startedAt()));
                    } else {
                        log.info("AI/RAG worker stream success command={} elapsed_ms={}",
                                List.of("llm-chat"), aiRagCliClient.elapsedMillis(command.startedAt()));
                    }
                    return;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writeSseError(outputStream, "채팅 처리가 중단되었습니다.");
        } finally {
            if (workerStarted) {
                aiRagCliClient.finishWorkerStream(command);
            } else {
                aiRagCliClient.cancelWorkerStream(command);
            }
        }
    }

    @Operation(summary = "대화 UUID 삭제")
    @DeleteMapping(value = "/llm/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteConversation(@RequestParam("thread_id") final String threadId) throws IOException {
        try {
            return json(aiRagCliClient.execute(List.of("llm-delete", "--thread-id", threadId), null));
        } finally {
            cancelRequestedThreads.remove(threadId);
            activeChatCommands.remove(threadId);
        }
    }

    private ResponseEntity<String> json(final String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private void writeSseError(final java.io.OutputStream outputStream, final String detail) throws IOException {
        String payload = objectMapper.writeValueAsString(new SseError(detail));
        outputStream.write(("event: error\ndata: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void writeSse(final java.io.OutputStream outputStream, final String event, final Object payload) throws IOException {
        outputStream.write(("event: " + event + "\ndata: " + objectMapper.writeValueAsString(payload) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private void writeRawSse(final java.io.OutputStream outputStream, final String event, final String jsonPayload) throws IOException {
        outputStream.write(("event: " + event + "\ndata: " + jsonPayload + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private boolean isCancelRequested(final String threadId) {
        return cancelRequestedThreads.contains(threadId);
    }

    private UserPrincipal requireUser(final UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION);
        }
        return userPrincipal;
    }

    private String markCancelled(final String threadId, final String reason) throws IOException {
        return aiRagCliClient.executeProcessOnly(List.of("llm-cancel", "--thread-id", threadId, "--reason", reason), null);
    }

    private void cancelCommand(final String threadId, final AiRagCliClient.RunningCommand command, final String reason) {
        cancelRequestedThreads.add(threadId);
        try {
            markCancelled(threadId, reason);
        } catch (Exception exception) {
            log.warn("AI/RAG cancel mark failed thread_id={} reason={}", threadId, reason, exception);
        }
        destroyCommand(command);
    }

    private void destroyCommand(final AiRagCliClient.RunningCommand command) {
        if (command != null && command.process() != null && command.process().isAlive()) {
            command.process().destroyForcibly();
        }
    }

    public record AnnouncementDeleteRequest(List<Long> id) {
    }

    public record LlmChatRequest(String thread_id, String message, Long announcement_id) {
    }

    public record LlmReplySaveRequest(String thread_id, Long announcement_id, String model_name, String reply) {
    }

    private record SseError(String detail) {
    }

    private record QueueStatus(String stage, int position, long waited_seconds) {
    }

    private record CancelStatus(String stage, String reason) {
    }

    private record StreamStatus(String stage, long last_seq) {
    }
}
