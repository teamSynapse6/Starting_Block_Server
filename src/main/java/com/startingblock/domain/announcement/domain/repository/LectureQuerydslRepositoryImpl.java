package com.startingblock.domain.announcement.domain.repository;

import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.domain.Lecture;

import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.announcement.dto.QRecommendLectureRes;
import com.startingblock.domain.announcement.dto.RecommendLectureRes;
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

    @Override
    public RecommendLectureRes findRandomLectureByUniversity(final Long userId, final University university) {
        return queryFactory
                .select(
                        new QRecommendLectureRes(
                                lecture.id,
                                roadmapLecture.lecture.id.isNotNull(),
                                lecture.title,
                                lecture.liberal,
                                lecture.credit,
                                lecture.session,
                                lecture.instructor,
                                lecture.content
                        )
                )
                .from(lecture)
                .leftJoin(roadmapLecture).on(lecture.id.eq(roadmapLecture.lecture.id).and(roadmapLecture.roadmap.user.id.eq(userId)))
                .where(
                        lecture.university.eq(university),
                        lecture.id.isNotNull()
                )
                .distinct()
                .orderBy(Expressions.numberTemplate(Double.class, "function('rand')").asc())
                .limit(1)
                .fetchOne();
    }

}