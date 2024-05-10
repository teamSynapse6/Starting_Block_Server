package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.Keyword;
import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.dto.*;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.announcement.exception.PermissionDeniedException;
import com.startingblock.domain.user.domain.Role;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.FeignConfig;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.infrastructure.feign.BizInfoClient;
import com.startingblock.global.infrastructure.feign.OpenDataClient;
import com.startingblock.global.infrastructure.feign.dto.BizInfoAnnouncementRes;
import com.startingblock.global.infrastructure.feign.dto.NewKStartUpAnnouncementRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementServiceImpl implements AnnouncementService {

    private final FeignConfig feignConfig;
    private final AnnouncementRepository announcementRepository;
    private final OpenDataClient openDataClient;
    private final BizInfoClient bizInfoClient;
    private final AnnouncementPdfUploader announcementPdfUploader;
    private final AnnouncementWriter announcementWriter;
    private final UserRepository userRepository;

    @Override
    @Transactional
    @Async
    public void refreshAnnouncementsV1(final UserPrincipal userPrincipal) {
        if (userPrincipal.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(Role.ADMIN.getValue())))
            throw new PermissionDeniedException();

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

    @Override
    public void uploadAnnouncementsFile(final UserPrincipal userPrincipal) {
        if (userPrincipal.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(Role.ADMIN.getValue())))
            throw new PermissionDeniedException();

        announcementPdfUploader.uploadPdf();
        announcementWriter.uploadPdfResultWrite();
    }

    @Override
    public Slice<AnnouncementRes> findAnnouncements(final UserPrincipal userPrincipal, final Pageable pageable, final String businessAge, final String region, final String supportType,
                                                    final String sort, final String search) {
        return announcementRepository.findAnnouncements(userPrincipal.getId(), pageable, businessAge, region, supportType, sort, search);
    }

    @Override
    public AnnouncementDetailRes findAnnouncementDetailById(final UserPrincipal userPrincipal, final Long announcementId) {
        AnnouncementDetailRes announcementDetail = announcementRepository.findAnnouncementDetail(userPrincipal.getId(), announcementId);

        if (announcementDetail == null)
            throw new InvalidAnnouncementException();

        return announcementDetail;
    }

    @Override
    public List<AnnouncementRes> findThreeRandomAnnouncement(final UserPrincipal userPrincipal) {
        return announcementRepository.findThreeRandomAnnouncement(userPrincipal.getId());
    }

    @Override
    public List<CustomAnnouncementRes> findCustomAnnouncement(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        University university = CampusFindService.findUserUniversity(user);

        // 교외1 + 교내1 -> user - university가 있고, 10개 대학 중 하나인 경우
        if (university != null) {
            log.info(university.toString());
            List<Announcement> announcementList = announcementRepository.findCustomAnnouncementOnOff(user, university);
            List<CustomAnnouncementRes> response = new ArrayList<>();
            boolean isOffCampus = true;

            for (Announcement announcement : announcementList) {
                LocalDateTime endDate = announcement.getEndDate();
                response.add(CustomAnnouncementRes.builder()
                                .announcementType(isOffCampus ? "교외" : "교내")
                                .keyword(isOffCampus ? announcement.getSprvInstClssCdNm() : String.valueOf(announcement.getKeyword()))
                                .title(announcement.getTitle())
                                .dday(String.valueOf((endDate != null) ? Duration.between(LocalDateTime.now(), endDate).toDays() : null))
                        .build());
                isOffCampus = false;
            }
            return response;
        }
        // 교외 2 -> user - university가 없고, 있어도 10개 대학이 아니면
        List<Announcement> announcementList = announcementRepository.findCustomAnnouncementOff(user);
        List<CustomAnnouncementRes> response = new ArrayList<>();
        for (Announcement announcement : announcementList) {
            LocalDateTime endDate = announcement.getEndDate();
            response.add(CustomAnnouncementRes.builder()
                    .announcementType("교외")
                    .keyword(announcement.getSprvInstClssCdNm())
                    .title(announcement.getTitle())
                    .dday(String.valueOf((endDate != null) ? Duration.between(LocalDateTime.now(), endDate).toDays() : null))
                    .build());
        }
        return response;
    }

    @Override
    public List<SystemRes> findSystems(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        return announcementRepository.findSystems(user.getId(), university);
    }

    @Override
    public List<OnCampusAnnouncementRes> findOnCampusAnnouncements(final UserPrincipal userPrincipal, final Keyword keyword) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        return announcementRepository.findOnCampusAnnouncements(userPrincipal.getId(), university, keyword);
    }

    @Override
    public List<LectureRes> findLectures(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        return announcementRepository.findLectures(userPrincipal.getId(), university);
    }
}
