package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.roadmap.domain.Roadmap;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long>, RoadmapQuerydslRepository {

    @EntityGraph(attributePaths = {"user"})
    Optional<Roadmap> findRoadmapById(Long roadmapId);

    @Modifying
    @Query("update Roadmap r set r.sequence = r.sequence - 1 where r.sequence > :deletedSequence and r.user.id = :userId")
    void bulkUpdateSequencesAfterDeletion(Integer deletedSequence, Long userId);

}
