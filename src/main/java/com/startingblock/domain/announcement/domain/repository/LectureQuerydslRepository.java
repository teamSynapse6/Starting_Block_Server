package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Lecture;
import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.announcement.dto.RecommendLectureRes;

import java.util.List;

public interface LectureQuerydslRepository {

    List<Lecture> findLecturesOfRoadmapsByRoadmapId(Long userId, Long roadmapId);
    RecommendLectureRes findRandomLectureByUniversity(Long userId, University university);

}
