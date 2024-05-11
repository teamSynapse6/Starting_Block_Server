package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.dto.SavedRoadmapRes;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;

import java.util.List;

public interface RoadmapQuerydslRepository {

    List<RoadmapDetailRes> findRoadmapDetailResponsesByUserId(Long userId);
    List<Roadmap> findRoadmapsByUserId(Long userId);
    List<SavedRoadmapRes> findAnnouncementSavedRoadmap(Long announcementId, Long userId);
    List<SavedRoadmapRes> findLectureSavedRoadmap(Long lectureId, Long id);

}
