package com.startingblock.domain.roadmap.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapAnnouncement;
import com.startingblock.domain.roadmap.domain.RoadmapStatus;
import com.startingblock.domain.roadmap.domain.repository.RoadmapAnnouncementRepository;
import com.startingblock.domain.roadmap.domain.repository.RoadmapRepository;
import com.startingblock.domain.roadmap.dto.AnnouncementSavedRoadmapRes;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;
import com.startingblock.domain.roadmap.dto.RoadmapRegisterReq;
import com.startingblock.domain.roadmap.dto.SwapRoadmapReq;
import com.startingblock.domain.roadmap.exception.*;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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

        // 삭제하는 Roadmap에 속한 RoadmapAnnouncement bulk delete
        roadmapAnnouncementRepository.bulkDeleteByRoadmapId(roadmapId);

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
    public List<AnnouncementSavedRoadmapRes> findAnnouncementSavedRoadmap(final UserPrincipal userPrincipal, final Long announcementId) {
        return roadmapRepository.findAnnouncementSavedRoadmap(announcementId, userPrincipal.getId());
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

}
