package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RoadmapAnnouncementRepository extends JpaRepository<RoadmapAnnouncement, Long> {

    boolean existsByRoadmapIdAndAnnouncementId(Long roadmapId, Long announcementId);

    Optional<RoadmapAnnouncement> findByRoadmapAndAnnouncement(Roadmap roadmap, Announcement announcement);

    @Modifying
    @Query("delete from RoadmapAnnouncement ra where ra.roadmap.id = :roadmapId")
    void bulkDeleteByRoadmapId(Long roadmapId);

}
