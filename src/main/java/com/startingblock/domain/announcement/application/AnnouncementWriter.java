package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.global.infrastructure.feign.PdfClient;
import com.startingblock.global.infrastructure.feign.dto.PdfResultRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementWriter {

    private final AnnouncementRepository announcementRepository;
    private final PdfClient pdfClient;

    @Transactional
    @Scheduled(cron = "0 10 3 * * *")
    public void uploadPdfResultWrite() {
        List<Announcement> announcements = announcementRepository.findFileUploadTargets();
        PdfResultRes uploadPdfResult = pdfClient.getUploadPdfResult();
        List<String> fileIds = uploadPdfResult.getFile_ids() == null ? Collections.emptyList() : uploadPdfResult.getFile_ids();

        fileIds.forEach(fileId -> announcements.stream()
                .filter(announcement -> announcement.getId().equals(Long.parseLong(fileId)))
                .findFirst()
                .ifPresent(announcement -> {
                    announcement.updateIsFileUploaded(true);
                }));
        log.info("AI/RAG 전처리 완료 플래그 동기화 targets={}, processedIds={}",
                announcements.size(), fileIds.size());
    }

    @Transactional
    public void save(List<Announcement> announcements) {
        if (announcements == null || announcements.isEmpty()) {
            return;
        }
        announcementRepository.saveAll(announcements);
    }
}
