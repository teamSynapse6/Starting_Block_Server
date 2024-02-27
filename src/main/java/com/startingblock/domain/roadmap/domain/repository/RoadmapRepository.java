package com.startingblock.domain.roadmap.domain.repository;

import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long>, RoadmapQuerydslRepository {

    @EntityGraph(attributePaths = {"user"})
    Optional<Roadmap> findRoadmapById(Long roadmapId);

}
