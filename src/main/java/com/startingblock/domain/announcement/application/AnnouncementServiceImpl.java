package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.dto.AnnouncementRes;
import com.startingblock.global.config.FeignConfig;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.infrastructure.feign.BizInfoClient;
import com.startingblock.global.infrastructure.feign.OpenDataClient;
import com.startingblock.global.infrastructure.feign.dto.BizInfoAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.KStartUpAnnouncementRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnnouncementServiceImpl implements AnnouncementService {

    private final FeignConfig feignConfig;
    private final AnnouncementRepository announcementRepository;
    private final OpenDataClient openDataClient;
    private final BizInfoClient bizInfoClient;

    @Override
    @Transactional
    public Void refreshAnnouncements() {
        String OPEN_DATA_SERVICE_KEY = feignConfig.getServiceKey().getOpenData();
        String BIZ_INFO_SERVICE_KEY = feignConfig.getServiceKey().getBizInfo();

        String nowDate = LocalDate.now().toString().replace("-", "");
        KStartUpAnnouncementRes openDataResponse = openDataClient.getAnnouncementList( // Open Data API 호출
                OPEN_DATA_SERVICE_KEY,
                "1",
                "1000",
                "20230101",
                nowDate,
                "Y",
                "json"
        );

        BizInfoAnnouncementRes bizInfoResponse = bizInfoClient.getAnnouncementList( // Biz Info API 호출
                BIZ_INFO_SERVICE_KEY,
                "json"
        );

        List<String> postIds = announcementRepository.findAnnouncementPostIds(); // Post ID 중복 체크용

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        List<Announcement> openDataAnnouncements = openDataResponse.getResponse().getBody().getItems().stream()
                .map(itemWrapper -> {
                    if (postIds.contains(itemWrapper.getItem().getPostsn())) {
                        return null;
                    }

                    return Announcement.builder()
                            .postSN(itemWrapper.getItem().getPostsn())
                            .bizTitle(itemWrapper.getItem().getBiztitle())
                            .supportType(itemWrapper.getItem().getSupporttype())
                            .title(itemWrapper.getItem().getTitle())
                            .areaName(itemWrapper.getItem().getAreaname())
                            .organizationName(itemWrapper.getItem().getOrganizationname())
                            .postTarget(itemWrapper.getItem().getPosttarget())
                            .postTargetAge(itemWrapper.getItem().getPosttargetage())
                            .postTargetComAge(itemWrapper.getItem().getPosttargetcomage())
                            .startDate(LocalDateTime.parse(itemWrapper.getItem().getStartdate(), dateTimeFormatter))
                            .endDate(LocalDateTime.parse(itemWrapper.getItem().getEnddate(), dateTimeFormatter))
                            .insertDate(LocalDateTime.parse(itemWrapper.getItem().getInsertdate(), dateTimeFormatter))
                            .detailUrl(itemWrapper.getItem().getDetailurl())
                            .prchCnAdrNo(itemWrapper.getItem().getPrchCnadrNo())
                            .sprvInstClssCdNm(itemWrapper.getItem().getSprvInstClssCdNm())
                            .bizPrchDprtNm(itemWrapper.getItem().getBizPrchDprtNm())
                            .blngGvDpCdNm(itemWrapper.getItem().getBlngGvdpCdNm())
                            .announcementType(AnnouncementType.OPEN_DATA)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

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
                .filter(Objects::nonNull)
                .toList();

        announcementRepository.saveAll(openDataAnnouncements);
        announcementRepository.saveAll(bizInfoAnnouncements);
        return null;
    }

    @Override
    public Slice<AnnouncementRes> findAnnouncements(final UserPrincipal userPrincipal, Pageable pageable, String businessAge, String region, String supportType, String sort, String search) {
        return announcementRepository.findAnnouncements(userPrincipal.getId(), pageable, businessAge, region, supportType, sort, search);
    }

}