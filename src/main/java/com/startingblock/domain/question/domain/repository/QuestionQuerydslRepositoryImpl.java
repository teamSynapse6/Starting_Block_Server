package com.startingblock.domain.question.domain.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.heart.domain.HeartType;
import com.startingblock.domain.question.domain.QQuestion;
import com.startingblock.domain.heart.domain.QHeart;
import com.startingblock.domain.answer.domain.QAnswer;
import com.startingblock.domain.question.dto.QuestionResponseDto;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class QuestionQuerydslRepositoryImpl implements QuestionQuerydslRepository{

    private final JPAQueryFactory queryFactory;

    @Override
    public List<QuestionResponseDto.QuestionListResponse> findQuestionListByAnnouncementId(Long announcementId) {
        QQuestion question = QQuestion.question;
        QHeart heart = QHeart.heart;
        QAnswer answer = QAnswer.answer;

        return queryFactory
                .select(Projections.constructor(QuestionResponseDto.QuestionListResponse.class,
                        question.content,
                        heart.id.count(),
                        answer.id.count()))
                .from(question)
                .leftJoin(heart).on(heart.question.eq(question).and(heart.heartType.eq(HeartType.QUESTION)))
                .leftJoin(answer).on(answer.question.eq(question))
                .where(question.announcement.id.eq(announcementId))
                .groupBy(question.id)
                .orderBy(heart.id.count().desc(), answer.id.count().desc(), question.createdAt.desc())
                .fetch();
    }
}
