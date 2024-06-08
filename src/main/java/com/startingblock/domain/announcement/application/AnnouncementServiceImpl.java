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
import org.springframework.scheduling.annotation.Scheduled;
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

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementPdfUploader announcementPdfUploader;
    private final AnnouncementWriter announcementWriter;
    private final UserRepository userRepository;

    @Override
    @Transactional
    @Async
    public void refreshAnnouncementsV1(final UserPrincipal userPrincipal) {
        if (userPrincipal.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals(Role.ADMIN.getValue())))
            throw new PermissionDeniedException();

        announcementWriter.refreshAnnouncements();
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
                                .detailUrl(announcement.getDetailUrl())
                                .announcementId(announcement.getId())
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
                    .detailUrl(announcement.getDetailUrl())
                    .announcementId(announcement.getId())
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
    public List<OnCampusAnnouncementRes> findOnCampusAnnouncements(final UserPrincipal userPrincipal, final String search, final Keyword keyword) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        return announcementRepository.findOnCampusAnnouncements(userPrincipal.getId(), university, search, keyword);
    }

    @Override
    public List<LectureRes> findLectures(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        return announcementRepository.findLectures(userPrincipal.getId(), university);
    }

    @Override
    public List<SupportGroupRes> findSupportGroups(final UserPrincipal userPrincipal, final Keyword keyword) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        List<Announcement> supportGroups = announcementRepository.findSupportGroups(userPrincipal.getId(), university, keyword);
        return SupportGroupRes.toDto(supportGroups);
    }

    @Override
    public List<String> findSupportGroupKeywords(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        return announcementRepository.findSupportGroupKeywords(university);
    }

}
