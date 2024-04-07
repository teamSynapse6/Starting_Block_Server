package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.global.infrastructure.feign.PdfClient;
import com.startingblock.global.infrastructure.feign.dto.PdfResultRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AnnouncementWriter {

    private final AnnouncementRepository announcementRepository;
    private final PdfClient pdfClient;

    @Transactional
    public void uploadPdfResultWrite() {
        List<Announcement> announcements = announcementRepository.findAnnouncementsByAnnouncementTypeAndIsFileUploaded(AnnouncementType.BIZ_INFO, false);
        PdfResultRes uploadPdfResult = pdfClient.getUploadPdfResult();

        uploadPdfResult.getFile_ids().forEach(fileId -> announcements.stream()
                .filter(announcement -> announcement.getId().equals(Long.parseLong(fileId)))
                .findFirst()
                .ifPresent(announcement -> {
                    announcement.updateIsFileUploaded(true);
                }));
    }

}
