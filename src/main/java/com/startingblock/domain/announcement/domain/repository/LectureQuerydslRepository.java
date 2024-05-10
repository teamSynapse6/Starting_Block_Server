package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Lecture;

import java.util.List;

public interface LectureQuerydslRepository {

    List<Lecture> findLecturesOfRoadmapsByRoadmapId(Long userId, Long roadmapId);

}
