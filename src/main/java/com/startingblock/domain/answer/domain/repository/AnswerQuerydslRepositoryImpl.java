package com.startingblock.domain.answer.domain.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.domain.QAnnouncement;
import com.startingblock.domain.answer.domain.QAnswer;
import com.startingblock.domain.answer.dto.AnswerResponseDto;
import com.startingblock.domain.heart.domain.QHeart;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.QQuestion;
import com.startingblock.domain.reply.domain.repository.ReplyRepository;
import com.startingblock.domain.reply.dto.ReplyResponseDto;
import com.startingblock.domain.user.domain.QUser;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class AnswerQuerydslRepositoryImpl implements AnswerQuerydslRepository {

    private final JPAQueryFactory queryFactory;
    private final ReplyRepository replyRepository;

    @Override
    public AnswerResponseDto.ContactAnswerResponse findContactAnswer(Long questionId) {

        QQuestion question = QQuestion.question;
        QAnswer answer = QAnswer.answer;
        QAnnouncement announcement = QAnnouncement.announcement;

        return queryFactory
                .select(Projections.constructor(AnswerResponseDto.ContactAnswerResponse.class,
                        answer.id,
                        announcement.prchCnAdrNo,
                        answer.content,
                        answer.createdAt))
                .from(answer)
                .join(answer.question, question)
                .join(question.announcement, announcement)
                .where(question.id.eq(questionId)
                        .and(question.questionType.eq(QAType.CONTACT))
                        .and(answer.answerType.eq(QAType.CONTACT)))
                .fetchOne();
    }

    @Override
    public List<AnswerResponseDto.AnswerListResponse> findAnswerList(Long questionId, Long userId) {
        QAnswer answer = QAnswer.answer;
        QUser user = QUser.user;
        QHeart heart = QHeart.heart;

        BooleanExpression isHeartedByUser = heart.answer.id.eq(answer.id).and(heart.user.id.eq(userId));

        // 먼저 답변 목록을 조회
        List<AnswerResponseDto.AnswerListResponse> answers = queryFactory
                .select(Projections.constructor(AnswerResponseDto.AnswerListResponse.class,
                        answer.id,
                        user.name,
                        answer.content,
                        answer.createdAt,
                        JPAExpressions.select(heart.count())
                                .from(heart)
                                .where(heart.answer.id.eq(answer.id)),
                        JPAExpressions.selectOne()
                                .from(heart)
                                .where(isHeartedByUser)
                                .exists()))
                .from(answer)
                .join(answer.user, user)
                .where(answer.question.id.eq(questionId))
                .fetch();

        // 각 답변에 대한 댓글 목록을 조회하여 결과에 추가
        answers.forEach(answerResponse -> {
            List<ReplyResponseDto.ReplyResponse> replies = replyRepository.findReplyListByAnswerId(answerResponse.getAnswerId(), userId);
            answerResponse.setReplyResponse(replies);
        });

        return answers;
    }
}
