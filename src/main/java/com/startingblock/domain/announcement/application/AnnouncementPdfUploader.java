package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.dto.PdfUploadReq;
import com.startingblock.global.infrastructure.feign.PdfClient;
import com.startingblock.global.infrastructure.feign.dto.PdfUploadRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementPdfUploader {

    private final PdfClient pdfClient;
    private final AnnouncementRepository announcementRepository;

    @Transactional
    public void uploadPdf() {
        List<Announcement> announcements = announcementRepository.findAnnouncementsByAnnouncementTypeAndIsFileUploaded(AnnouncementType.BIZ_INFO, false);

        RestClient restClient = RestClient.create();
        List<PdfUploadReq> req = new ArrayList<>();

        announcements.forEach(announcement -> {
            String filename = restClient.get().uri(announcement.getFileUrl()).retrieve().toBodilessEntity().getHeaders().getContentDisposition().getFilename();
            if (filename == null) {
                return;
            }

            int index = filename.lastIndexOf(".");
            String extension = filename.substring(index + 1);

            req.add(PdfUploadReq.builder()
                    .id(announcement.getId())
                    .url(announcement.getFileUrl())
                    .format(extension)
                    .build());
        });

        PdfUploadRes res = pdfClient.uploadPdf(req);
        res.getSuccess_items().forEach(id -> {
            Announcement announcement = announcementRepository.findById(id).orElseThrow();
            announcement.updateIsFileUploaded(true);
            announcementRepository.save(announcement);
        });
    }

}
