package com.startingblock.domain.gpt.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startingblock.domain.gpt.dto.DuplicateReq;
import com.startingblock.domain.gpt.dto.GroupingQuestionReq;
import com.startingblock.global.infrastructure.airag.AiRagCliClient;
import com.startingblock.global.infrastructure.airag.AiRagGpuConcurrencyLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class GptService {

    private final ObjectMapper objectMapper;
    private final AiRagCliClient aiRagCliClient;
    private final AiRagGpuConcurrencyLimiter gpuConcurrencyLimiter;

    public Long checkDuplicateQuestion(final DuplicateReq duplicateReq) throws JsonProcessingException {
        AiRagGpuConcurrencyLimiter.Lease lease = null;
        try {
            lease = acquireGpuSlot("question-duplicate");
            String response = aiRagCliClient.execute(List.of("question-duplicate"), duplicateReq.toJson());
            JsonNode jsonNode = objectMapper.readTree(response);
            long questionId = jsonNode.path("questionId").asLong(0L);
            log.info("Ollama duplicate question response={}", questionId);
            return questionId;
        } catch (IOException | RuntimeException exception) {
            log.warn("Ollama duplicate question failed. fallback questionId=0", exception);
            return 0L;
        } finally {
            releaseGpuSlot(lease);
        }
    }

    public String groupingQuestions(final GroupingQuestionReq questionList) throws JsonProcessingException {
        AiRagGpuConcurrencyLimiter.Lease lease = null;
        try {
            lease = acquireGpuSlot("question-group");
            String response = aiRagCliClient.execute(List.of("question-group"), questionList.toJson());
            objectMapper.readValue(response, new TypeReference<List<Map<String, Object>>>() {
            });
            log.info("Ollama grouped questions responseChars={}", response.length());
            return response;
        } catch (IOException | RuntimeException exception) {
            log.warn("Ollama grouped questions failed. fallback single-question groups", exception);
            return objectMapper.writeValueAsString(questionList.getQuestions().stream()
                    .filter(question -> question.getQuestionId() != null && question.getContent() != null && !question.getContent().isBlank())
                    .map(question -> Map.of(
                            "questionId", List.of(question.getQuestionId()),
                            "content", question.getContent()
                    ))
                    .toList());
        } finally {
            releaseGpuSlot(lease);
        }
    }

    private AiRagGpuConcurrencyLimiter.Lease acquireGpuSlot(String command) {
        gpuConcurrencyLimiter.enterQueue();
        long startedAt = System.nanoTime();
        try {
            while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt) < gpuConcurrencyLimiter.waitTimeoutSeconds()) {
                AiRagGpuConcurrencyLimiter.Lease lease = gpuConcurrencyLimiter.tryAcquire();
                if (lease != null) {
                    log.info("AI/RAG GPU slot acquired command={}", command);
                    return lease;
                }
                try {
                    Thread.sleep(gpuConcurrencyLimiter.waitIntervalMs());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("AI/RAG GPU slot wait interrupted", exception);
                }
            }
            throw new IllegalStateException("AI/RAG GPU slot wait timed out: " + command);
        } finally {
            gpuConcurrencyLimiter.leaveQueue();
        }
    }

    private void releaseGpuSlot(AiRagGpuConcurrencyLimiter.Lease lease) {
        if (lease != null) {
            gpuConcurrencyLimiter.release();
        }
    }
}
