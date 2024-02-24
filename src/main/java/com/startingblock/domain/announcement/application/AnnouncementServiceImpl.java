package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.global.infrastructure.feign.BizInfoClient;
import com.startingblock.global.infrastructure.feign.OpenDataClient;
import com.startingblock.global.infrastructure.feign.dto.BizInfoAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.KStartUpAnnouncementRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final OpenDataClient openDataClient;
    private final BizInfoClient bizInfoClient;

    @Override
    @Transactional
    public Void refreshAnnouncements() {
        KStartUpAnnouncementRes openDataResponse = openDataClient.getAnnouncementList(
                "kGDrnuZPC2TpPVNJQB6kvqkgvPfZzOlgv%2FkxZa%2FaG58hcYBwWM1QgLZYVBkoxMt6vkU1z4r3E4nIhwH3%2FNDlqw%3D%3D",
                "1",
                "1000",
                "20230101",
                "20240219",
                "Y",
                "json"
        );

        BizInfoAnnouncementRes bizInfoResponse = bizInfoClient.getAnnouncementList(
                "2EZC42",
                "json"
        );

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        List<Announcement> openDataAnnouncements = openDataResponse.getResponse().getBody().getItems().stream()
                .map(itemWrapper -> Announcement.builder()
                        .postSN(itemWrapper.getItem().getPostsn())
                        .bizTitle(itemWrapper.getItem().getBiztitle())
                        .supportType(itemWrapper.getItem().getSupporttype())
                        .title(itemWrapper.getItem().getTitle())
                        .areaName(itemWrapper.getItem().getAreaname())
                        .organizationName(itemWrapper.getItem().getOrganizationname())
                        .postTarget(itemWrapper.getItem().getPosttarget())
                        .postTargetComAge(itemWrapper.getItem().getPosttargetcomage())
                        .startDate(LocalDateTime.parse(itemWrapper.getItem().getStartdate(), dateTimeFormatter))
                        .endDate(LocalDateTime.parse(itemWrapper.getItem().getEnddate(), dateTimeFormatter))
                        .insertDate(LocalDateTime.parse(itemWrapper.getItem().getInsertdate(), dateTimeFormatter))
                        .detailUrl(itemWrapper.getItem().getDetailurl())
                        .prchCnAdrNo(itemWrapper.getItem().getPrchcnadrno())
                        .sprvInstClssCdNm(itemWrapper.getItem().getSprvinstclsscdnm())
                        .bizPrchDprtNm(itemWrapper.getItem().getBizprchdprtnm())
                        .blngGvDpCdNm(itemWrapper.getItem().getBlnggvdpcdnm())
                        .announcementType(AnnouncementType.OPEN_DATA)
                        .build())
                .toList();

        List<Announcement> bizInfoAnnouncements = bizInfoResponse.getJsonArray().stream()
                .map(item -> {
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
                    }

                    return Announcement.builder()
                            .postSN(item.getPblancId())
                            .bizTitle(item.getPblancNm())
                            .supportType(item.getPldirSportRealmMlsfcCodeNm())
                            .title(item.getPblancNm())
                            .areaName(item.getJrsdInsttNm())
                            .organizationName(item.getExcInsttNm())
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
                .toList();

        announcementRepository.saveAll(openDataAnnouncements);
        announcementRepository.saveAll(bizInfoAnnouncements);
        return null;
    }

}
