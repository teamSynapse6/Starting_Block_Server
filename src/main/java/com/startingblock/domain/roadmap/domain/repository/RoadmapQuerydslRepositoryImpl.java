package com.startingblock.domain.roadmap.domain.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.roadmap.dto.QRoadmapDetailRes;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.startingblock.domain.roadmap.domain.QRoadmap.*;

@RequiredArgsConstructor
public class RoadmapQuerydslRepositoryImpl implements RoadmapQuerydslRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<RoadmapDetailRes> findRoadmapsByUserId(final Long userId) {
        return queryFactory
                .select(
                        new QRoadmapDetailRes(
                                roadmap.id,
                                roadmap.title,
                                roadmap.sequence
                        )
                )
                .from(roadmap)
                .where(roadmap.user.id.eq(userId))
                .orderBy(roadmap.sequence.asc())
                .fetch();
    }

}
