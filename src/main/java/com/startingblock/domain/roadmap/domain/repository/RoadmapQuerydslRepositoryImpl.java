package com.startingblock.domain.roadmap.domain.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.domain.QAnnouncement;
import com.startingblock.domain.roadmap.domain.QRoadmapAnnouncement;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.dto.AnnouncementSavedRoadmapRes;
import com.startingblock.domain.roadmap.dto.QAnnouncementSavedRoadmapRes;
import com.startingblock.domain.roadmap.dto.QRoadmapDetailRes;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.startingblock.domain.announcement.domain.QAnnouncement.*;
import static com.startingblock.domain.roadmap.domain.QRoadmap.*;
import static com.startingblock.domain.roadmap.domain.QRoadmapAnnouncement.*;

@RequiredArgsConstructor
public class RoadmapQuerydslRepositoryImpl implements RoadmapQuerydslRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<RoadmapDetailRes> findRoadmapDetailResponsesByUserId(final Long userId) {
        return queryFactory
                .select(
                        new QRoadmapDetailRes(
                                roadmap.id,
                                roadmap.title,
                                roadmap.roadmapStatus,
                                roadmap.sequence
                        )
                )
                .from(roadmap)
                .where(roadmap.user.id.eq(userId))
                .orderBy(roadmap.sequence.asc())
                .fetch();
    }

    @Override
    public List<Roadmap> findRoadmapsByUserId(final Long userId) {
        return queryFactory
                .selectFrom(roadmap)
                .where(roadmap.user.id.eq(userId))
                .orderBy(roadmap.sequence.asc())
                .fetch();
    }

    @Override
    public List<AnnouncementSavedRoadmapRes> findAnnouncementSavedRoadmap(final Long announcementId, final Long userId) {
        return queryFactory
                .select(
                        new QAnnouncementSavedRoadmapRes(
                                roadmap.id,
                                roadmap.title,
                                roadmapAnnouncement.announcement.id.isNotNull()
                        )
                )
                .from(roadmap)
                .leftJoin(roadmapAnnouncement).on(
                        roadmapAnnouncement.roadmap.id.eq(roadmap.id),
                        roadmapAnnouncement.announcement.id.eq(announcementId)
                )
                .where(roadmap.user.id.eq(userId))
                .distinct()
                .fetch();
    }

}
