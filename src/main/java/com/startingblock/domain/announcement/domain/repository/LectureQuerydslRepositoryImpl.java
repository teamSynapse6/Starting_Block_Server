package com.startingblock.domain.announcement.domain.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.domain.Lecture;

import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.startingblock.domain.announcement.domain.QLecture.*;
import static com.startingblock.domain.roadmap.domain.QRoadmap.*;
import static com.startingblock.domain.roadmap.domain.QRoadmapLecture.*;

@RequiredArgsConstructor
public class LectureQuerydslRepositoryImpl implements LectureQuerydslRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Lecture> findLecturesOfRoadmapsByRoadmapId(final Long userId, final Long roadmapId) {
        return queryFactory
                .select(lecture)
                .from(roadmap)
                .leftJoin(roadmapLecture).on(roadmap.id.eq(roadmapLecture.roadmap.id))
                .leftJoin(lecture).on(roadmapLecture.lecture.id.eq(lecture.id))
                .where(
                        roadmap.id.eq(roadmapId),
                        roadmap.user.id.eq(userId),
                        lecture.id.isNotNull()
                )
                .fetch();
    }

}