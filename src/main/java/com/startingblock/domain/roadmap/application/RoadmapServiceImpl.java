package com.startingblock.domain.roadmap.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.Lecture;
import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.domain.repository.LectureRepository;
import com.startingblock.domain.announcement.dto.AnnouncementRes;
import com.startingblock.domain.announcement.dto.RecommendAnnouncementRes;
import com.startingblock.domain.announcement.dto.RoadmapLectureRes;
import com.startingblock.domain.roadmap.dto.*;
import com.startingblock.domain.announcement.dto.RoadmapSystemRes;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.announcement.exception.InvalidLectureException;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapAnnouncement;
import com.startingblock.domain.roadmap.domain.RoadmapLecture;
import com.startingblock.domain.roadmap.domain.RoadmapStatus;
import com.startingblock.domain.roadmap.domain.repository.RoadmapAnnouncementRepository;
import com.startingblock.domain.roadmap.domain.repository.RoadmapLectureRepository;
import com.startingblock.domain.roadmap.domain.repository.RoadmapRepository;
import com.startingblock.domain.roadmap.exception.*;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadmapServiceImpl implements RoadmapService {

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final RoadmapRepository roadmapRepository;
    private final AnnouncementRepository announcementRepository;
    private final RoadmapAnnouncementRepository roadmapAnnouncementRepository;
    private final LectureRepository lectureRepository;
    private final RoadmapLectureRepository roadmapLectureRepository;

    @Override
    @Transactional
    public void registerRoadmaps(final UserPrincipal userPrincipal, final RoadmapRegisterReq roadMapRegisterReq) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        List<Roadmap> roadmaps = roadMapRegisterReq.getRoadmaps().stream()
                .map(roadmapReq -> {
                    RoadmapStatus status = RoadmapStatus.NOT_STARTED;
                    if (roadmapReq.getSequence().equals(0))
                        status = RoadmapStatus.IN_PROGRESS;

                    return Roadmap.builder()
                            .title(roadmapReq.getTitle())
                            .sequence(roadmapReq.getSequence())
                            .roadmapStatus(status)
                            .user(user)
                            .build();
                })
                .toList();

        if (roadmaps.isEmpty())
            throw new EmptyRoadmapException();

        roadmapRepository.saveAll(roadmaps);
    }

    @Override
    @Transactional
    public List<RoadmapDetailRes> deleteRoadmap(final UserPrincipal userPrincipal, final  Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findRoadmapById(roadmapId)
                .orElseThrow(InvalidRoadmapException::new);

        Integer deletedSequence = roadmap.getSequence();
        RoadmapStatus deletedRoadmapStatus = roadmap.getRoadmapStatus();

        // 삭제하는 Roadmap에 속한 RoadmapAnnouncement, RoadmapLecture bulk delete
        roadmapAnnouncementRepository.bulkDeleteByRoadmapId(roadmapId);
        roadmapLectureRepository.bulkDeleteByRoadmapId(roadmapId);

        // 삭제하는 Roadmap
        roadmapRepository.delete(roadmap);

        // 삭제 후 sequence bulk update
        roadmapRepository.bulkUpdateSequencesAfterDeletion(deletedSequence, userPrincipal.getId());

        // DB와 영속성 컨텍스트 동기화
        entityManager.clear();

        List<Roadmap> updatedRoadmaps = roadmapRepository.findRoadmapsByUserId(userPrincipal.getId());

        if(!updatedRoadmaps.isEmpty() && updatedRoadmaps.size() > deletedSequence) {
            Roadmap afterDeletionRoadmap;
            afterDeletionRoadmap = updatedRoadmaps.get(deletedSequence);

            if (deletedRoadmapStatus.equals(RoadmapStatus.IN_PROGRESS))
                afterDeletionRoadmap.updateRoadmapStatus(RoadmapStatus.IN_PROGRESS);
        }

        return RoadmapDetailRes.toRoadmapDetailResList(updatedRoadmaps);
    }

    @Override
    @Transactional
    public void addRoadmapAnnouncement(final UserPrincipal userPrincipal, final Long roadmapId, final Long announcementId) {
        Roadmap roadmap = roadmapRepository.findRoadmapById(roadmapId)
                .orElseThrow(EmptyRoadmapException::new);

        if (!Objects.equals(roadmap.getUser().getId(), userPrincipal.getId()))
            throw new RoadmapMismatchUserException();

        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(InvalidAnnouncementException::new);

        if (roadmapAnnouncementRepository.existsByRoadmapIdAndAnnouncementId(roadmapId, announcementId))
            throw new AlreadyExistsRoadmapException();

        RoadmapAnnouncement roadmapAnnouncement = RoadmapAnnouncement.builder()
                .roadmap(roadmap)
                .announcement(announcement)
                .build();

        announcement.addRoadmapCount();
        roadmapAnnouncementRepository.save(roadmapAnnouncement);
    }

    @Override
    @Transactional
    public void deleteRoadmapAnnouncement(final UserPrincipal userPrincipal, final Long roadmapId, final Long announcementId) {
        Roadmap roadmap = roadmapRepository.findRoadmapById(roadmapId)
                .orElseThrow(EmptyRoadmapException::new);

        if (!Objects.equals(roadmap.getUser().getId(), userPrincipal.getId()))
            throw new RoadmapMismatchUserException();

        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(InvalidAnnouncementException::new);

        RoadmapAnnouncement roadmapAnnouncement = roadmapAnnouncementRepository.findByRoadmapAndAnnouncement(roadmap, announcement)
                .orElseThrow(InvalidAnnouncementRoadmapException::new);

        announcement.subtractRoadmapCount();
        roadmapAnnouncementRepository.delete(roadmapAnnouncement);
    }

    @Override
    public List<RoadmapDetailRes> findRoadmaps(final UserPrincipal userPrincipal) {
        return roadmapRepository.findRoadmapDetailResponsesByUserId(userPrincipal.getId());
    }

    @Override
    public List<SavedRoadmapRes> findAnnouncementSavedRoadmap(final UserPrincipal userPrincipal, final Long announcementId) {
        return roadmapRepository.findAnnouncementSavedRoadmap(announcementId, userPrincipal.getId());
    }

    @Override
    public List<SavedRoadmapRes> findLectureSavedRoadmap(final UserPrincipal userPrincipal, final Long lectureId) {
        return roadmapRepository.findLectureSavedRoadmap(lectureId, userPrincipal.getId());
    }

    @Override
    @Transactional
    public List<RoadmapDetailRes> swapRoadmap(final UserPrincipal userPrincipal, final SwapRoadmapReq swapRoadmapReq) {
        List<Roadmap> roadmaps = roadmapRepository.findRoadmapsByUserId(userPrincipal.getId());

        ConcurrentHashMap<Long, Roadmap> roadmapMap = new ConcurrentHashMap<>();
        for (Roadmap roadmap : roadmaps) {
            roadmapMap.put(roadmap.getId(), roadmap);
        }

        List<Long> swapRoadmapIds = swapRoadmapReq.getRoadmapIds();

        int swapIdx = 0;
        for(Long roadmapId : swapRoadmapIds) {
            roadmapMap.get(roadmapId).updateSequence(swapIdx++);
        }

        roadmaps.sort(Comparator.comparing(Roadmap::getSequence));

        boolean isInProgressAppeared = false;
        for (Roadmap roadmap : roadmaps) {
            if(roadmap.getRoadmapStatus().equals(RoadmapStatus.IN_PROGRESS)) {
                isInProgressAppeared = true;
                continue;
            }

            if(!isInProgressAppeared) {
                roadmap.updateRoadmapStatus(RoadmapStatus.COMPLETED);
            }
        }

        return RoadmapDetailRes.toRoadmapDetailResList(roadmaps);
    }

    @Override
    @Transactional
    public List<RoadmapDetailRes> leapCurrentRoadmap(final UserPrincipal userPrincipal) {
        List<Roadmap> roadmaps = roadmapRepository.findRoadmapsByUserId(userPrincipal.getId());

        boolean isInProgressAppeared = false;
        for (Roadmap roadmap : roadmaps) {
            if (roadmap.getRoadmapStatus().equals(RoadmapStatus.IN_PROGRESS)) {
                roadmap.updateRoadmapStatus(RoadmapStatus.COMPLETED);
                isInProgressAppeared = true;
                continue;
            }

            if(isInProgressAppeared) {
                roadmap.updateRoadmapStatus(RoadmapStatus.IN_PROGRESS);
                break;
            }
        }

        return RoadmapDetailRes.toRoadmapDetailResList(roadmaps);
    }

    @Override
    @Transactional
    public List<RoadmapDetailRes> addRoadmap(final UserPrincipal userPrincipal, final String roadmapTitle) {
        List<Roadmap> roadmaps = roadmapRepository.findRoadmapsByUserId(userPrincipal.getId());
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        RoadmapStatus status = RoadmapStatus.NOT_STARTED;
        int maxSequence = roadmaps.size() - 1;

        if (!roadmaps.isEmpty()) {
            Roadmap lastRoadmap = roadmaps.get(maxSequence);
            if (lastRoadmap.getRoadmapStatus().equals(RoadmapStatus.COMPLETED))
                status = RoadmapStatus.IN_PROGRESS;
        }

        Roadmap newRoadmap = Roadmap.builder()
                .title(roadmapTitle)
                .sequence(maxSequence + 1)
                .roadmapStatus(status)
                .user(user)
                .build();

        roadmapRepository.save(newRoadmap);
        roadmaps.add(newRoadmap);

        return RoadmapDetailRes.toRoadmapDetailResList(roadmaps);
    }

    @Override
    public List<?> findListOfRoadmap(final UserPrincipal userPrincipal, final Long roadmapId, final String type) {
        List<Announcement> announcements = announcementRepository.findListOfRoadmapByRoadmapId(userPrincipal.getId(), roadmapId, type);

        if(type.equals("OFF-CAMPUS"))
            return RoadmapAnnouncementRes.toDto(announcements);
        else if(type.equals("ON-CAMPUS"))
            return RoadmapOnCampusRes.toDto(announcements);
        else
            return RoadmapSystemRes.toDto(announcements);
    }

    @Override
    @Transactional
    public void addRoadmapLecture(final UserPrincipal userPrincipal, final Long roadmapId, final Long lectureId) {
        Roadmap roadmap = roadmapRepository.findRoadmapById(roadmapId)
                .orElseThrow(InvalidRoadmapException::new);

        if (!Objects.equals(roadmap.getUser().getId(), userPrincipal.getId()))
            throw new RoadmapMismatchUserException();

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(InvalidLectureException::new);

        if (roadmapLectureRepository.existsByRoadmapIdAndLectureId(roadmapId, lectureId))
            throw new AlreadyExistsRoadmapException();

        RoadmapLecture roadmapLecture = RoadmapLecture.builder()
                .roadmap(roadmap)
                .lecture(lecture)
                .build();

        lecture.addRoadmapCount();
        roadmapLectureRepository.save(roadmapLecture);
    }

    @Override
    public List<RoadmapLectureRes> findLecturesOfRoadmap(final UserPrincipal userPrincipal, final Long roadmapId) {
        List<Lecture> lectures = lectureRepository.findLecturesOfRoadmapsByRoadmapId(userPrincipal.getId(), roadmapId);

        if(lectures.isEmpty())
            throw new EmptyRoadmapException();

        return RoadmapLectureRes.toDto(lectures);
    }

    @Override
    @Transactional
    public void deleteRoadmapLecture(final UserPrincipal userPrincipal, final Long roadmapId, final Long lectureId) {
        Roadmap roadmap = roadmapRepository.findRoadmapById(roadmapId)
                .orElseThrow(EmptyRoadmapException::new);

        if (!Objects.equals(roadmap.getUser().getId(), userPrincipal.getId()))
            throw new RoadmapMismatchUserException();

        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(InvalidAnnouncementException::new);

        RoadmapLecture roadmapLecture = roadmapLectureRepository.findByRoadmapAndLecture(roadmap, lecture)
                .orElseThrow(InvalidAnnouncementRoadmapException::new);

        lecture.subtractRoadmapCount();
        roadmapLectureRepository.delete(roadmapLecture);
    }

    @Override
    public List<RecommendAnnouncementRes> recommendOffCampusAnnouncements(final UserPrincipal userPrincipal, final Long roadmapId) {
        Roadmap roadmap = roadmapRepository.findById(roadmapId)
                .orElseThrow(InvalidRoadmapException::new);

        String supportType = roadmap.getTitle();

        List<Announcement> recommendations = new ArrayList<>();
        int remainingSlots = 3;

        // 1순위: 로드맵 Title명에 따라 일치하는 supportType에서 진행중인 공고 반환
        List<Announcement> activeAnnouncements = announcementRepository.findOffCampusAnnouncementsBySupportType(supportType);
        if (!activeAnnouncements.isEmpty()) {
            int count = Math.min(remainingSlots, activeAnnouncements.size());
            recommendations.addAll(activeAnnouncements.subList(0, count));
            remainingSlots -= count;
        }

        // 2순위: 로드맵에 저장하기가 많은 공고 반환
        if (remainingSlots > 0) {
            List<Announcement> popularAnnouncements = announcementRepository.findOffCampusAnnouncementsByRoadmapCount();
            if (!popularAnnouncements.isEmpty()) {
                int count = Math.min(remainingSlots, popularAnnouncements.size());
                recommendations.addAll(popularAnnouncements.subList(0, count));
                remainingSlots -= count;
            }
        }

        // 3순위: 업로드일자가 가장 최신인 공고 반환
        if (remainingSlots > 0) {
            List<Announcement> latestAnnouncements = announcementRepository.findOffCampusAnnouncementsByInsertDate();
            if (!latestAnnouncements.isEmpty()) {
                int count = Math.min(remainingSlots, latestAnnouncements.size());
                recommendations.addAll(latestAnnouncements.subList(0, count));
            }
        }

        return RecommendAnnouncementRes.toDto(recommendations);
    }

    @Override
    public List<RecommendAnnouncementRes> recommendOnCampusAnnouncements(final UserPrincipal userPrincipal, final Long roadmapId) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        University university = University.of(user.getUniversity());

        List<Announcement> recommendAnnouncements = announcementRepository.findThreeRandomOnCampusAnnouncements(university);
        return RecommendAnnouncementRes.toDto(recommendAnnouncements);
    }

}
