package com.startingblock.domain.question.domain.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.heart.domain.HeartType;
import com.startingblock.domain.question.domain.QAType;
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
    public List<QuestionResponseDto.QuestionListResponse> findQuestionListByAnnouncementId(Long userId, Long announcementId) {
        QQuestion question = QQuestion.question;
        QHeart heart = QHeart.heart;
        QHeart subHeart = new QHeart("subHeart");
        QAnswer answer = QAnswer.answer;

        return queryFactory
                .select(Projections.constructor(QuestionResponseDto.QuestionListResponse.class,
                        question.id,
                        question.content,
                        JPAExpressions.select(answer.count())
                                .from(answer)
                                .where(answer.question.eq(question)),
                        JPAExpressions.selectOne()
                                .from(answer)
                                .where(answer.question.eq(question),
                                        answer.answerType.eq(QAType.CONTACT))
                                .exists(),
                        JPAExpressions.select(subHeart.count())
                                .from(subHeart)
                                .where(subHeart.question.eq(question),
                                        subHeart.heartType.eq(HeartType.QUESTION)),
                        JPAExpressions.selectOne()
                                .from(subHeart)
                                .where(subHeart.question.eq(question),
                                        subHeart.user.id.eq(userId),
                                        subHeart.heartType.eq(HeartType.QUESTION))
                                .exists(),
                        JPAExpressions.select(subHeart.id.max())
                                .from(subHeart)
                                .where(subHeart.user.id.eq(userId).and(subHeart.question.id.eq(question.id)))))
                .from(question)
                .leftJoin(heart).on(heart.question.eq(question))
                .leftJoin(answer).on(answer.question.eq(question))
                .where(question.announcement.id.eq(announcementId))
                .groupBy(question.id)
                .orderBy(heart.count().desc(), answer.count().desc(), question.createdAt.desc())
                .fetch();
    }
}
