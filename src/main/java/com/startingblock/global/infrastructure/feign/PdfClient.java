package com.startingblock.global.infrastructure.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.global.infrastructure.airag.AiRagCliClient;
import com.startingblock.global.infrastructure.feign.dto.PdfResultRes;
import com.startingblock.global.infrastructure.feign.dto.PdfUploadRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PdfClient {

    private final ObjectMapper objectMapper;
    private final AiRagCliClient aiRagCliClient;

    public PdfUploadRes uploadPdf(final List<PdfUploadReq> pdfUploadReq) {
        try {
            String input = objectMapper.writeValueAsString(pdfUploadReq);
            String output = aiRagCliClient.execute(List.of("upload"), input);
            return objectMapper.readValue(extractJsonObject(output), PdfUploadRes.class);
        } catch (IOException exception) {
            throw new IllegalStateException("AI/RAG upload command failed", exception);
        }
    }

    public PdfResultRes getUploadPdfResult() {
        try {
            String output = aiRagCliClient.execute(List.of("validation"), null);
            return objectMapper.readValue(extractJsonObject(output), PdfResultRes.class);
        } catch (IOException exception) {
            throw new IllegalStateException("AI/RAG validation command failed", exception);
        }
    }

    private String extractJsonObject(final String output) throws IOException {
        if (output == null || output.isBlank()) {
            throw new IOException("AI/RAG command returned empty output");
        }

        String trimmed = output.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IOException("AI/RAG command output did not contain JSON object");
        }
        return trimmed.substring(start, end + 1);
    }
}
