package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.global.infrastructure.feign.PdfClient;
import com.startingblock.global.infrastructure.feign.dto.PdfUploadRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementPdfUploader {

    private final PdfClient pdfClient;
    private final AnnouncementRepository announcementRepository;

    @Transactional
    @Scheduled(cron = "0 5 3 * * *")
    public void uploadPdf() {
        List<Announcement> announcements = announcementRepository.findFileUploadTargets();

        RestClient restClient = RestClient.create();
        List<PdfUploadReq> req = new ArrayList<>();

        announcements.forEach(announcement -> {
            try {
                String extension = resolveExtension(restClient, announcement.getFileUrl());
                if (extension == null) {
                    log.warn("공고 파일 확장자 확인 실패 announcementId={}, fileUrl={}", announcement.getId(), announcement.getFileUrl());
                    return;
                }
                if (isIgnoredExtension(extension)) {
                    log.info("이미지 파일 업로드 제외 announcementId={}, extension={}", announcement.getId(), extension);
                    return;
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

        PdfUploadRes uploadRes = pdfClient.uploadPdf(req);
        Set<Long> indexingFailedItems = uploadRes.getIndexing_failed_items() == null
                ? Collections.emptySet()
                : uploadRes.getIndexing_failed_items().stream().collect(Collectors.toSet());
        Set<Long> successItems = uploadRes.getSuccess_items() == null
                ? Collections.emptySet()
                : uploadRes.getSuccess_items().stream()
                .filter(id -> !indexingFailedItems.contains(id))
                .collect(Collectors.toSet());

        announcements.stream()
                .filter(announcement -> successItems.contains(announcement.getId()))
                .forEach(announcement -> announcement.updateIsFileUploaded(true));

        log.info("AI/RAG 업로드 완료 requested={}, success={}, failed={}, indexingFailed={}",
                req.size(),
                uploadRes.getSuccess_items() == null ? 0 : uploadRes.getSuccess_items().size(),
                uploadRes.getFailed_items() == null ? 0 : uploadRes.getFailed_items().size(),
                uploadRes.getIndexing_failed_items() == null ? 0 : uploadRes.getIndexing_failed_items().size());
    }

    private String resolveExtension(RestClient restClient, String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }

        try {
            String filename = restClient.get().uri(fileUrl).retrieve().toBodilessEntity()
                    .getHeaders().getContentDisposition().getFilename();
            String extension = extractExtension(filename);
            if (extension != null) {
                return extension;
            }
        } catch (Exception exception) {
            log.warn("공고 파일 HEAD 요청 실패 fileUrl={}", fileUrl, exception);
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

    private boolean isIgnoredExtension(String extension) {
        return Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp").contains(extension);
    }

    private boolean isSupportedExtension(String extension) {
        return Set.of("pdf", "hwp", "hwpx", "txt").contains(extension);
    }

}
