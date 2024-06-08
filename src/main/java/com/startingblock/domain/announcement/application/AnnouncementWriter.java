package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.global.config.FeignConfig;
import com.startingblock.global.infrastructure.feign.BizInfoClient;
import com.startingblock.global.infrastructure.feign.OpenDataClient;
import com.startingblock.global.infrastructure.feign.PdfClient;
import com.startingblock.global.infrastructure.feign.dto.BizInfoAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.NewKStartUpAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.PdfResultRes;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AnnouncementWriter {

    private final AnnouncementRepository announcementRepository;
    private final PdfClient pdfClient;
    private final OpenDataClient openDataClient;
    private final BizInfoClient bizInfoClient;
    private final FeignConfig feignConfig;

    @Transactional
    @Scheduled(cron = "0 10 3 * * *")
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

    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void refreshAnnouncements() {
        String OPEN_DATA_SERVICE_KEY = feignConfig.getServiceKey().getOpenData();
        String BIZ_INFO_SERVICE_KEY = feignConfig.getServiceKey().getBizInfo();

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate now = LocalDate.now();

        List<String> postIds = announcementRepository.findAnnouncementPostIds(); // Post ID 중복 체크용
        List<String> openDataPostIds = new ArrayList<>();

        final int perPage = 2000; // 페이지 당 공고 수 설정

        List<Announcement> openDataAnnouncements = new ArrayList<>();
        for (int page = 1; page <= 2; page++) {
            NewKStartUpAnnouncementRes response = openDataClient.getNewAnnouncementList(
                    OPEN_DATA_SERVICE_KEY,
                    String.valueOf(page),
                    String.valueOf(perPage),
                    "json"
            );

            List<Announcement> announcements = response.getData().stream()
                    .map(item -> {
                        if (item.getPbancRcptBgngDt() == null || item.getPbancRcptEndDt() == null) {
                            return null;
                        }

                        LocalDate startDate = LocalDate.parse(item.getPbancRcptBgngDt(), dateFormatter);
                        LocalDate endDate = LocalDate.parse(item.getPbancRcptEndDt(), dateFormatter);
                        if (endDate.isBefore(now)) {
                            return null;
                        }

                        String postSn = item.getDetlPgUrl().split("pbancSn=")[1];
                        if (postIds.contains(postSn) || openDataPostIds.contains(postSn)) {
                            return null;
                        }

                        openDataPostIds.add(postSn);

                        return Announcement.builder()
                                .postSN(postSn)
                                .bizTitle(item.getBizPbancNm())
                                .supportType(item.getSuptBizClsfc())
                                .title(item.getBizPbancNm())
                                .content(item.getPbancCtnt())
                                .areaName(item.getSuptRegin())
                                .organizationName(item.getPbancNtrpNm())
                                .postTarget(item.getAplyTrgt())
                                .postTargetAge(item.getBizTrgtAge())
                                .postTargetComAge(item.getBizEnyy())
                                .startDate(startDate.atStartOfDay())
                                .endDate(endDate.atStartOfDay())
                                .detailUrl(item.getDetlPgUrl())
                                .prchCnAdrNo(item.getPrchCnplNo())
                                .sprvInstClssCdNm(item.getSprvInst())
                                .bizPrchDprtNm(item.getBizPrchDprtNm())
                                .announcementType(AnnouncementType.OPEN_DATA)
                                .build();
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            openDataAnnouncements.addAll(announcements);
        }

        BizInfoAnnouncementRes bizInfoResponse = bizInfoClient.getAnnouncementList( // Biz Info API 호출
                BIZ_INFO_SERVICE_KEY,
                "json"
        );

        List<Announcement> bizInfoAnnouncements = bizInfoResponse.getJsonArray().stream()
                .map(item -> {
                    if (postIds.contains(item.getPblancId())) {
                        return null;
                    }

                    LocalDateTime startDateTime = null, endDateTime = null;
                    String nonDate = null;

                    try {
                        String[] dates = item.getReqstBeginEndDe().split("~");
                        if (dates.length == 2) { // 날짜 형식이라고 가정할 때
                            LocalDate startDate = LocalDate.parse(dates[0].trim(), dateFormatter);
                            LocalDate endDate = LocalDate.parse(dates[1].trim(), dateFormatter);
                            startDateTime = startDate.atStartOfDay();
                            endDateTime = endDate.atStartOfDay();
                        } else {
                            throw new DateTimeParseException("날짜 형식 아님", item.getReqstBeginEndDe(), 0);
                        }
                    } catch (DateTimeParseException e) {
                        nonDate = item.getReqstBeginEndDe();
                        startDateTime = LocalDateTime.parse(item.getCreatPnttm(), dateTimeFormatter);
                    }

                    return Announcement.builder()
                            .postSN(item.getPblancId())
                            .bizTitle(item.getPblancNm())
                            .fileUrl(item.getPrintFlpthNm())
                            .supportType(item.getPldirSportRealmMlsfcCodeNm())
                            .title(item.getPblancNm())
                            .areaName(item.getJrsdInsttNm())
                            .organizationName(item.getExcInsttNm())
                            .content(item.getBsnsSumryCn())
                            .postTarget(item.getTrgetNm())
                            .startDate(startDateTime)
                            .endDate(endDateTime)
                            .nonDate(nonDate)
                            .insertDate(LocalDateTime.parse(item.getCreatPnttm(), dateTimeFormatter))
                            .detailUrl("https://www.bizinfo.go.kr" + item.getPblancUrl())
                            .prchCnAdrNo(item.getRefrncNm())
                            .sprvInstClssCdNm(item.getJrsdInsttNm())
                            .bizPrchDprtNm(item.getExcInsttNm())
                            .blngGvDpCdNm(item.getJrsdInsttNm())
                            .announcementType(AnnouncementType.BIZ_INFO)
                            .build();
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        announcementRepository.saveAll(openDataAnnouncements);
        announcementRepository.saveAll(bizInfoAnnouncements);
    }

}
