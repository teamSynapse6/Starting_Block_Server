package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.dto.AnnouncementSavedRoadmapRes;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;

import java.util.List;

public interface RoadmapQuerydslRepository {

    List<RoadmapDetailRes> findRoadmapDetailResponsesByUserId(Long userId);
    List<Roadmap> findRoadmapsByUserId(Long userId);
    List<AnnouncementSavedRoadmapRes> findAnnouncementSavedRoadmap(Long announcementId, Long userId);

}
