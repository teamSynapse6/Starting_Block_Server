package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.roadmap.domain.Roadmap;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {

    @EntityGraph(attributePaths = {"user"})
    Optional<Roadmap> findRoadmapById(Long roadmapId);

}
