package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoadmapAnnouncementRepository extends JpaRepository<RoadmapAnnouncement, Long> {

    boolean existsByRoadmapIdAndAnnouncementId(Long roadmapId, Long announcementId);
    Optional<RoadmapAnnouncement> findByRoadmapAndAnnouncement(Roadmap roadmap, Announcement announcement);

}
