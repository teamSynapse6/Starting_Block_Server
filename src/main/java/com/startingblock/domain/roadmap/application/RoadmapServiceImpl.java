package com.startingblock.domain.roadmap.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapAnnouncement;
import com.startingblock.domain.roadmap.domain.RoadmapStatus;
import com.startingblock.domain.roadmap.domain.repository.RoadmapAnnouncementRepository;
import com.startingblock.domain.roadmap.domain.repository.RoadmapRepository;
import com.startingblock.domain.roadmap.dto.RoadmapAddReq;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;
import com.startingblock.domain.roadmap.dto.RoadmapRegisterReq;
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
    public List<RoadmapDetailRes> registerRoadmaps(final UserPrincipal userPrincipal, final RoadmapRegisterReq roadMapRegisterReq) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        boolean roadmapCheck = roadmapRepository.existsByUser(user);
        if (roadmapCheck)
            throw new RegistrationCompletedException();

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

        List<Roadmap> registeredRoadmaps = roadmapRepository.findRoadmapsByUserId(userPrincipal.getId());

        registeredRoadmaps.sort(Comparator.comparing(Roadmap::getSequence));
        return RoadmapDetailRes.toRoadmapDetailResList(registeredRoadmaps);
    }

    @Override
    @Transactional
    public List<RoadmapDetailRes> deleteRoadmap(final UserPrincipal userPrincipal, final Long roadmapId) {
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

        if (deletedSequence.equals(updatedRoadmaps.size()))
            return RoadmapDetailRes.toRoadmapDetailResList(updatedRoadmaps);

        updatedRoadmaps.sort(Comparator.comparing(Roadmap::getSequence));

        Roadmap afterDeletionRoadmap = updatedRoadmaps.get(deletedSequence);

        if (deletedRoadmapStatus.equals(RoadmapStatus.IN_PROGRESS))
            afterDeletionRoadmap.updateRoadmapStatus(RoadmapStatus.IN_PROGRESS);

        return RoadmapDetailRes.toRoadmapDetailResList(updatedRoadmaps);
    }

    @Override
    @Transactional
    public List<RoadmapDetailRes> addRoadmap(final UserPrincipal userPrincipal, final RoadmapAddReq roadmapAddReq) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        List<Roadmap> roadmaps = roadmapRepository.findRoadmapsByUserId(userPrincipal.getId());

        if(roadmaps.isEmpty()) {
            Roadmap newRoadmap = Roadmap.builder()
                    .title(roadmapAddReq.getTitle())
                    .sequence(0)
                    .roadmapStatus(RoadmapStatus.IN_PROGRESS)
                    .user(user)
                    .build();

            roadmapRepository.save(newRoadmap);
            return RoadmapDetailRes.toRoadmapDetailResList(List.of(newRoadmap));
        }

        roadmaps.sort(Comparator.comparing(Roadmap::getSequence));
        Roadmap lastRoadmap = roadmaps.get(roadmaps.size() - 1);

        RoadmapStatus roadmapStatus = RoadmapStatus.NOT_STARTED;
        if (lastRoadmap.getRoadmapStatus().equals(RoadmapStatus.COMPLETED))
            roadmapStatus = RoadmapStatus.IN_PROGRESS;

        Roadmap newRoadmap = Roadmap.builder()
                .title(roadmapAddReq.getTitle())
                .sequence(lastRoadmap.getSequence() + 1)
                .roadmapStatus(roadmapStatus)
                .user(user)
                .build();
        roadmapRepository.save(newRoadmap);

        roadmaps.add(newRoadmap);
        return RoadmapDetailRes.toRoadmapDetailResList(roadmaps);
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

}
