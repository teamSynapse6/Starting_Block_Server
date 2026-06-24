package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.mail.application.ContactEmailExtractor;
import com.startingblock.global.config.FeignConfig;
import com.startingblock.global.infrastructure.feign.BizInfoClient;
import com.startingblock.global.infrastructure.feign.OpenDataClient;
import com.startingblock.global.infrastructure.feign.dto.BizInfoAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.KStartUpAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.NewKStartUpAnnouncementRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnnouncementManager {

    private static final String BIZ_INFO_BASE_URL = "https://www.bizinfo.go.kr";

    private final OpenDataClient openDataClient;
    private final BizInfoClient bizInfoClient;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementWriter announcementWriter;
    private final FeignConfig feignConfig;
    private final KStartupAttachmentExtractor kStartupAttachmentExtractor;

    @Scheduled(cron = "0 00 03 * * *")
    public void refreshAnnouncements() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate now = LocalDate.now();

        List<String> postIds = announcementRepository.findAnnouncementPostIds(); // Post ID 중복 체크용
        List<String> openDataPostIds = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CompletableFuture<List<Announcement>> openDataFuture = CompletableFuture.supplyAsync(
                () -> getOpenDataAnnouncementsSync(dateFormatter, now, postIds, openDataPostIds), executor)
                .exceptionally(exception -> {
                    log.warn("K-Startup 공고 갱신 실패", exception);
                    return List.of();
                });

        CompletableFuture<List<Announcement>> bizInfoFuture = CompletableFuture.supplyAsync(
                () -> getBizInfoAnnouncementsSync(postIds, dateFormatter, dateTimeFormatter), executor)
                .exceptionally(exception -> {
                    log.warn("BizInfo 공고 갱신 실패", exception);
                    return List.of();
                });

        try {
            List<Announcement> openDataAnnouncements = openDataFuture.join();
            List<Announcement> bizInfoAnnouncements = bizInfoFuture.join();

            announcementWriter.save(openDataAnnouncements);
            announcementWriter.save(bizInfoAnnouncements);

            log.info("공고 갱신 완료 openDataSaved={}, bizInfoSaved={}", openDataAnnouncements.size(), bizInfoAnnouncements.size());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    public List<Announcement> getOpenDataAnnouncementsSync(DateTimeFormatter dateFormatter, LocalDate now, List<String> postIds, List<String> openDataPostIds) {
        List<Announcement> openDataAnnouncements = new ArrayList<>();
        String openDataServiceKey = feignConfig.getServiceKey().resolveOpenDataRequestKey();
        for (int page = 1; page <= 2; page++) {
            try {
                NewKStartUpAnnouncementRes response = openDataClient.getNewAnnouncementList(
                        openDataServiceKey,
                        String.valueOf(page),
                        String.valueOf(200),
                        "json"
                );

                List<NewKStartUpAnnouncementRes.Item> data = Optional.ofNullable(response.getData()).orElseGet(List::of);
                log.info("K-Startup 공고 조회 page={}, currentCount={}, totalCount={}, dataSize={}",
                        page, response.getCurrentCount(), response.getTotalCount(), data.size());

                List<Announcement> announcements = data.stream()
                        .map(item -> {
                            try {
                                return toOpenDataAnnouncement(item, dateFormatter, now, postIds, openDataPostIds);
                            } catch (Exception exception) {
                                log.warn("K-Startup 공고 변환 실패 id={}, detailUrl={}, title={}",
                                        item.getId(), item.getDetlPgUrl(), item.getBizPbancNm(), exception);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                openDataAnnouncements.addAll(announcements);
                log.info("K-Startup 신규 공고 후보 page={}, count={}", page, announcements.size());
            } catch (Exception exception) {
                log.warn("K-Startup 신규 API 조회 실패 page={}, legacy fallback 실행", page, exception);
                return getLegacyOpenDataAnnouncementsSync(dateFormatter, now, postIds, openDataPostIds);
            }
        }
        return openDataAnnouncements;
    }

    private Announcement toOpenDataAnnouncement(NewKStartUpAnnouncementRes.Item item, DateTimeFormatter dateFormatter, LocalDate now,
                                                List<String> postIds, List<String> openDataPostIds) {
        if (item.getPbancRcptBgngDt() == null || item.getPbancRcptEndDt() == null) {
            return null;
        }

        LocalDate startDate = parseOpenDataDate(item.getPbancRcptBgngDt(), dateFormatter);
        LocalDate endDate = parseOpenDataDate(item.getPbancRcptEndDt(), dateFormatter);
        if (endDate.isBefore(now)) {
            return null;
        }

        String postSn = resolveOpenDataPostSn(item);
        if (postSn == null || postSn.isBlank()) {
            log.warn("K-Startup 공고 postSN 추출 실패 detailUrl={}, id={}", item.getDetlPgUrl(), item.getId());
            return null;
        }
        if (postIds.contains(postSn) || openDataPostIds.contains(postSn)) {
            return null;
        }

        openDataPostIds.add(postSn);

        String detailUrl = item.getDetlPgUrl();
        return Announcement.builder()
                .postSN(postSn)
                .fileUrl(kStartupAttachmentExtractor.extractFirstDownloadUrl(detailUrl).orElse(null))
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
                .detailUrl(detailUrl)
                .prchCnAdrNo(item.getPrchCnplNo())
                .sprvInstClssCdNm(item.getSprvInst())
                .bizPrchDprtNm(item.getBizPrchDprtNm())
                .announcementType(AnnouncementType.OPEN_DATA)
                .build();
    }

    private List<Announcement> getLegacyOpenDataAnnouncementsSync(DateTimeFormatter dateFormatter, LocalDate now,
                                                                  List<String> postIds, List<String> openDataPostIds) {
        List<Announcement> openDataAnnouncements = new ArrayList<>();
        String openDataServiceKey = feignConfig.getServiceKey().resolveOpenDataRequestKey();
        for (int page = 1; page <= 5; page++) {
            KStartUpAnnouncementRes response = openDataClient.getAnnouncementList(
                    openDataServiceKey,
                    String.valueOf(page),
                    String.valueOf(100),
                    now.minusYears(1).format(dateFormatter),
                    now.plusYears(1).format(dateFormatter),
                    "Y",
                    "json"
            );

            List<KStartUpAnnouncementRes.Response.Body.ItemWrapper> data = Optional.ofNullable(response)
                    .map(KStartUpAnnouncementRes::getResponse)
                    .map(KStartUpAnnouncementRes.Response::getBody)
                    .map(KStartUpAnnouncementRes.Response.Body::getItems)
                    .orElseGet(List::of);
            log.info("K-Startup legacy 공고 조회 page={}, dataSize={}", page, data.size());

            List<Announcement> announcements = data.stream()
                    .map(KStartUpAnnouncementRes.Response.Body.ItemWrapper::getItem)
                    .filter(Objects::nonNull)
                    .map(item -> {
                        try {
                            return toLegacyOpenDataAnnouncement(item, dateFormatter, now, postIds, openDataPostIds);
                        } catch (Exception exception) {
                            log.warn("K-Startup legacy 공고 변환 실패 postSn={}, detailUrl={}, title={}",
                                    item.getPostsn(), item.getDetailurl(), item.getTitle(), exception);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            openDataAnnouncements.addAll(announcements);
            if (data.size() < 100) {
                break;
            }
        }
        return openDataAnnouncements;
    }

    private Announcement toLegacyOpenDataAnnouncement(KStartUpAnnouncementRes.Response.Body.Item item,
                                                      DateTimeFormatter dateFormatter, LocalDate now,
                                                      List<String> postIds, List<String> openDataPostIds) {
        if (item.getStartdate() == null || item.getEnddate() == null) {
            return null;
        }

        LocalDate startDate = parseOpenDataDate(item.getStartdate(), dateFormatter);
        LocalDate endDate = parseOpenDataDate(item.getEnddate(), dateFormatter);
        if (endDate.isBefore(now)) {
            return null;
        }

        String postSn = item.getPostsn();
        if (postSn == null || postSn.isBlank() || postIds.contains(postSn) || openDataPostIds.contains(postSn)) {
            return null;
        }
        openDataPostIds.add(postSn);

        String detailUrl = item.getDetailurl();
        return Announcement.builder()
                .postSN(postSn)
                .fileUrl(kStartupAttachmentExtractor.extractFirstDownloadUrl(detailUrl).orElse(null))
                .bizTitle(item.getBiztitle())
                .supportType(item.getSupporttype())
                .title(item.getTitle())
                .content(item.getTitle())
                .areaName(item.getAreaname())
                .organizationName(item.getOrganizationname())
                .postTarget(item.getPosttarget())
                .postTargetAge(item.getPosttargetage())
                .postTargetComAge(item.getPosttargetcomage())
                .startDate(startDate.atStartOfDay())
                .endDate(endDate.atStartOfDay())
                .insertDate(parseOpenDataDateTime(item.getInsertdate(), dateFormatter))
                .detailUrl(detailUrl)
                .prchCnAdrNo(item.getPrchCnadrNo())
                .sprvInstClssCdNm(item.getSprvInstClssCdNm())
                .bizPrchDprtNm(item.getBizPrchDprtNm())
                .blngGvDpCdNm(item.getBlngGvdpCdNm())
                .announcementType(AnnouncementType.OPEN_DATA)
                .build();
    }

    private LocalDate parseOpenDataDate(String value, DateTimeFormatter dateFormatter) {
        if (value == null || value.isBlank()) {
            throw new DateTimeParseException("empty date", "", 0);
        }
        String normalized = value.trim();
        try {
            return LocalDate.parse(normalized, dateFormatter);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        if (normalized.length() >= 10) {
            return LocalDate.parse(normalized.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        }
        throw new DateTimeParseException("unsupported date", normalized, 0);
    }

    private LocalDateTime parseOpenDataDateTime(String value, DateTimeFormatter dateFormatter) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {
            return parseOpenDataDate(value, dateFormatter).atStartOfDay();
        }
    }

    private String resolveOpenDataPostSn(NewKStartUpAnnouncementRes.Item item) {
        if (item.getId() != null && !item.getId().isBlank()) {
            return item.getId().trim();
        }

        String detailUrl = item.getDetlPgUrl();
        if (detailUrl == null || detailUrl.isBlank()) {
            return null;
        }

        String[] parameterNames = {"pbancSn=", "pbanc_sn=", "id="};
        for (String parameterName : parameterNames) {
            int index = detailUrl.indexOf(parameterName);
            if (index < 0) {
                continue;
            }
            String value = detailUrl.substring(index + parameterName.length());
            int endIndex = value.indexOf('&');
            return endIndex >= 0 ? value.substring(0, endIndex) : value;
        }
        return null;
    }

    public List<Announcement> getBizInfoAnnouncementsSync(List<String> postIds, DateTimeFormatter dateFormatter, DateTimeFormatter dateTimeFormatter) {
        BizInfoAnnouncementRes bizInfoResponse = bizInfoClient.getAnnouncementList(
                feignConfig.getServiceKey().getBizInfo(),
                "json",
                100
        );

        List<BizInfoAnnouncementRes.Item> data = Optional.ofNullable(bizInfoResponse.getJsonArray()).orElseGet(List::of);
        log.info("BizInfo 공고 조회 dataSize={}", data.size());

        return data.stream()
                .map(item -> {
                    try {
                        return toBizInfoAnnouncement(item, postIds, dateFormatter, dateTimeFormatter);
                    } catch (Exception exception) {
                        log.warn("BizInfo 공고 변환 실패 id={}, title={}", item.getPblancId(), item.getPblancNm(), exception);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Announcement toBizInfoAnnouncement(BizInfoAnnouncementRes.Item item, List<String> postIds,
                                               DateTimeFormatter dateFormatter, DateTimeFormatter dateTimeFormatter) {
        if (item.getPblancId() == null || item.getPblancId().isBlank() || postIds.contains(item.getPblancId())) {
            return null;
        }

        LocalDateTime startDateTime = null, endDateTime = null;
        String nonDate = null;

        try {
            String[] dates = item.getReqstBeginEndDe().split("~");
            if (dates.length == 2) {
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
                .detailUrl(resolveBizInfoDetailUrl(item.getPblancUrl()))
                .prchCnAdrNo(item.getRefrncNm())
                .contact(ContactEmailExtractor.extract(item.getRefrncNm()).orElse(null))
                .sprvInstClssCdNm(item.getJrsdInsttNm())
                .bizPrchDprtNm(item.getExcInsttNm())
                .blngGvDpCdNm(item.getJrsdInsttNm())
                .announcementType(AnnouncementType.BIZ_INFO)
                .build();
    }

    private String resolveBizInfoDetailUrl(String pblancUrl) {
        if (pblancUrl == null || pblancUrl.isBlank()) {
            return null;
        }

        String normalized = pblancUrl.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("//")) {
            return "https:" + normalized;
        }
        if (normalized.startsWith("/")) {
            return BIZ_INFO_BASE_URL + normalized;
        }
        return BIZ_INFO_BASE_URL + "/" + normalized;
    }
}
