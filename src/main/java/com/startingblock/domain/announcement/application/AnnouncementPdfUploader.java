package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
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
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementPdfUploader {

    private final PdfClient pdfClient;
    private final AnnouncementRepository announcementRepository;

    @Transactional
    @Scheduled(cron = "0 5 4 * * *")
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
            if(extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg")) {
                return;
            }

            req.add(PdfUploadReq.builder()
                    .id(announcement.getId())
                    .url(announcement.getFileUrl())
                    .format(extension)
                    .build());
        });

        pdfClient.uploadPdf(req);
    }

}
