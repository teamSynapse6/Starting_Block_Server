package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.global.infrastructure.feign.PdfClient;
import com.startingblock.global.infrastructure.feign.dto.PdfUploadRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementPdfUploader {

    private final PdfClient pdfClient;
    private final AnnouncementRepository announcementRepository;

    @Value("${ai-rag.upload.batch-size:20}")
    private int uploadBatchSize;

    @Scheduled(cron = "0 5 3 * * *")
    public void uploadPdf() {
        List<Announcement> announcements = announcementRepository.findFileUploadTargets();

        RestClient restClient = RestClient.create();
        List<PdfUploadReq> req = new ArrayList<>();

        announcements.forEach(announcement -> {
            try {
                String extension = resolveExtension(restClient, announcement.getFileUrl());
                if (extension == null) {
                    extension = "unknown";
                }
                if (!isSupportedExtension(extension)) {
                    log.warn("지원하지 않는 공고 파일 형식 announcementId={}, extension={}, fileUrl={}",
                            announcement.getId(), extension, announcement.getFileUrl());
                    return;
                }

                req.add(PdfUploadReq.builder()
                        .id(announcement.getId())
                        .url(announcement.getFileUrl())
                        .format(extension)
                        .build());
            } catch (Exception exception) {
                log.warn("공고 파일 업로드 요청 생성 실패 announcementId={}, fileUrl={}",
                        announcement.getId(), announcement.getFileUrl(), exception);
            }
        });

        if (req.isEmpty()) {
            log.info("AI/RAG 업로드 대상 공고 파일이 없습니다.");
            return;
        }

        int successCount = 0;
        int failedCount = 0;
        int indexingFailedCount = 0;
        int indexingQueuedCount = 0;

        int batchSize = Math.max(uploadBatchSize, 1);
        for (int start = 0; start < req.size(); start += batchSize) {
            List<PdfUploadReq> batch = req.subList(start, Math.min(start + batchSize, req.size()));
            try {
                PdfUploadRes uploadRes = pdfClient.uploadPdf(batch);
                successCount += uploadRes.getSuccess_items() == null ? 0 : uploadRes.getSuccess_items().size();
                failedCount += uploadRes.getFailed_items() == null ? 0 : uploadRes.getFailed_items().size();
                indexingFailedCount += uploadRes.getIndexing_failed_items() == null ? 0 : uploadRes.getIndexing_failed_items().size();
                indexingQueuedCount += uploadRes.getIndexing_queued_items() == null ? 0 : uploadRes.getIndexing_queued_items().size();
            } catch (Exception exception) {
                failedCount += batch.size();
                log.warn("AI/RAG 업로드 batch 실패 start={}, size={}", start, batch.size(), exception);
            }
        }

        log.info("AI/RAG 업로드 완료 requested={}, success={}, failed={}, indexingQueued={}, indexingFailed={}",
                req.size(),
                successCount,
                failedCount,
                indexingQueuedCount,
                indexingFailedCount);
    }

    private String resolveExtension(RestClient restClient, String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }

        try {
            String filename = restClient.method(HttpMethod.HEAD).uri(fileUrl).retrieve().toBodilessEntity()
                    .getHeaders().getContentDisposition().getFilename();
            String extension = extractExtension(filename);
            if (extension != null) {
                return extension;
            }
        } catch (Exception exception) {
            log.debug("공고 파일 HEAD 요청 실패 fileUrl={}", fileUrl, exception);
        }

        return extractExtension(fileUrl);
    }

    private String extractExtension(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.split("\\?")[0].split("#")[0];
        int index = normalized.lastIndexOf(".");
        if (index < 0 || index == normalized.length() - 1) {
            return null;
        }
        return normalized.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isSupportedExtension(String extension) {
        return Set.of(
                "pdf", "hwp", "hwpx", "txt",
                "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff", "heic", "heif",
                "do", "unknown"
        ).contains(extension);
    }

}
