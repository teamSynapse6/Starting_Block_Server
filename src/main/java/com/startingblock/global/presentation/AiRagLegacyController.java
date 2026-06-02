package com.startingblock.global.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.global.infrastructure.airag.AiRagCliClient;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.concurrent.TimeUnit;

@Tag(name = "AI/RAG Legacy API", description = "기존 PDFGPT FastAPI 라우트 호환 API")
@RestController
@RequiredArgsConstructor
@Slf4j
public class AiRagLegacyController {

    private final AiRagCliClient aiRagCliClient;
    private final ObjectMapper objectMapper;

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
    public ResponseEntity<String> startConversation() throws IOException {
        return json(aiRagCliClient.execute(List.of("llm-start"), null));
    }

    @Operation(summary = "RAG 기반 LLM 채팅")
    @PostMapping(value = "/llm/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chat(@RequestBody final LlmChatRequest request) throws IOException {
        String stdin = objectMapper.writeValueAsString(request);
        AiRagCliClient.RunningCommand command = aiRagCliClient.startStreaming(List.of("llm-chat"), stdin);

        StreamingResponseBody body = outputStream -> {
            if (command.workerStream()) {
                streamWorkerFrames(command, outputStream);
                return;
            }

            try (InputStream inputStream = command.process().getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                    outputStream.flush();
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
            }
        };

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    private void streamWorkerFrames(final AiRagCliClient.RunningCommand command, final java.io.OutputStream outputStream) throws IOException {
        try {
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
            aiRagCliClient.finishWorkerStream(command);
        }
    }

    @Operation(summary = "대화 UUID 삭제")
    @DeleteMapping(value = "/llm/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> deleteConversation(@RequestParam("thread_id") final String threadId) throws IOException {
        return json(aiRagCliClient.execute(List.of("llm-delete", "--thread-id", threadId), null));
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

    public record AnnouncementDeleteRequest(List<Long> id) {
    }

    public record LlmChatRequest(String thread_id, String message, Long announcement_id) {
    }

    private record SseError(String detail) {
    }
}
