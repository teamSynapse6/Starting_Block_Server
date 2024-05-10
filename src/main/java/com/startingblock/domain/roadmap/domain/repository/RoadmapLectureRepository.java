package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.announcement.domain.Lecture;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapLecture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RoadmapLectureRepository extends JpaRepository<RoadmapLecture, Long> {

    boolean existsByRoadmapIdAndLectureId(Long roadmapId, Long lectureId);

    Optional<RoadmapLecture> findByRoadmapAndLecture(Roadmap roadmap, Lecture lecture);

    @Modifying
    @Query("delete from RoadmapLecture rl where rl.roadmap.id = :roadmapId")
    void bulkDeleteByLectureId(Long roadmapId);

}
