package com.startingblock.domain.roadmap.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapAnnouncement;
import com.startingblock.domain.roadmap.domain.RoadmapStatus;
import com.startingblock.domain.roadmap.domain.repository.RoadmapAnnouncementRepository;
import com.startingblock.domain.roadmap.domain.repository.RoadmapRepository;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;
import com.startingblock.domain.roadmap.dto.RoadmapRegisterReq;
import com.startingblock.domain.roadmap.exception.AlreadyExistsRoadmapException;
import com.startingblock.domain.roadmap.exception.EmptyRoadmapException;
import com.startingblock.domain.roadmap.exception.InvalidAnnouncementRoadmapException;
import com.startingblock.domain.roadmap.exception.RoadmapMismatchUserException;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadmapServiceImpl implements RoadmapService {

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
        return roadmapRepository.findRoadmapsByUserId(userPrincipal.getId());
    }

}
