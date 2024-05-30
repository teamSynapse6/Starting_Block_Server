package com.startingblock.domain.question.domain.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.heart.domain.HeartType;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.QQuestion;
import com.startingblock.domain.heart.domain.QHeart;
import com.startingblock.domain.answer.domain.QAnswer;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.dto.QuestionResponseDto;
import com.startingblock.domain.reply.domain.QReply;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.startingblock.domain.announcement.domain.QAnnouncement.*;
import static com.startingblock.domain.roadmap.domain.QRoadmapAnnouncement.*;
import static com.startingblock.domain.question.domain.QQuestion.*;
import static com.startingblock.domain.heart.domain.QHeart.*;

@RequiredArgsConstructor
@Slf4j
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

    @Override
    public List<QuestionResponseDto.QuestionByMyAnswerAndReply> findQuestionByMyAnswerAndReply(Long userId) {
        QQuestion question = QQuestion.question;
        QAnswer answer = QAnswer.answer;
        QReply reply = QReply.reply;

        List<QuestionResponseDto.QuestionByMyAnswerAndReply> answers = queryFactory
                .select(Projections.constructor(QuestionResponseDto.QuestionByMyAnswerAndReply.class,
                        question.announcement,
                        question.user,
                        question.id,
                        question.content,
                        answer.id))
                .from(answer)
                .join(answer.question, question)
                .where(answer.user.id.eq(userId))
                .fetch();
        answers.forEach(myAnswer -> {
            myAnswer.setWriteType("Answer");
        });

        List<QuestionResponseDto.QuestionByMyAnswerAndReply> replies = queryFactory
                .select(Projections.constructor(QuestionResponseDto.QuestionByMyAnswerAndReply.class,
                        question.announcement,
                        question.user,
                        question.id,
                        question.content,
                        reply.id))
                .from(reply)
                .join(reply.answer, answer)
                .join(answer.question, question)
                .where(reply.user.id.eq(userId))
                .fetch();
        replies.forEach(myReplies -> {
            myReplies.setWriteType("Reply");
        });

        List<QuestionResponseDto.QuestionByMyAnswerAndReply> allAnswersAndReplies = Stream.concat(answers.stream(), replies.stream())
                .distinct()
                .collect(Collectors.toList());

        return allAnswersAndReplies;
    }

    @Override
    public List<Question> findQuestionsForStatusCheck(Long userId) {
        QQuestion question = QQuestion.question;
        return queryFactory
                .selectFrom(question)
                .where(question.questionType.eq(QAType.CONTACT)
                        .and(question.user.id.eq(userId))
                        .and(isAnsweredAndOutdated(question).not()))
                .fetch();
    }
    private BooleanExpression isAnsweredAndOutdated(QQuestion question) {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        return question.isAnswerd.isTrue()
                .and(question.updatedAt.before(yesterday));
    }

    @Override
    public List<Question> findQuestionWaitingAnswerOff(Long userId) {

        // userId가 roadmap에 있는지 확인하여 정렬 가중치 계산
        NumberExpression<Integer> userRoadmapPriority = new CaseBuilder()
                .when(roadmapAnnouncement.roadmap.user.id.eq(userId)).then(1)
                .otherwise(0);
        // 공고에 대한 로드맵 저장 횟수를 계산
        NumberExpression<Long> roadmapCount = roadmapAnnouncement.count();
        // heart 개수를 계산하는 집계
        NumberExpression<Integer> heartsCount = new CaseBuilder()
                .when(heart.heartType.eq(HeartType.QUESTION))
                .then(1)
                .otherwise(0)
                .sum();

        // 모든 공고를 조회하면서 저장한 공고, 로드맵 저장된 공고, 하트 누적 순으로 정렬
        List<Announcement> rankedAnnouncements = queryFactory
                .selectFrom(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement))
                .leftJoin(question).on(question.announcement.eq(announcement))
                .leftJoin(heart).on(heart.question.eq(question))
                .where(announcement.announcementType.in(AnnouncementType.OPEN_DATA, AnnouncementType.BIZ_INFO))
                .groupBy(announcement.id)
                .orderBy(
                        userRoadmapPriority.desc(),
                        roadmapCount.desc(),
                        heartsCount.desc(),
                        announcement.createdAt.desc()
                )
                .limit(2)  // 마지막에 상위 2개의 공고만 선택
                .fetch();

        // 각 선택된 공고에 대해 하트가 가장 많은 질문 조회
        List<Question> topQuestions = new ArrayList<>();
        for (Announcement announcement : rankedAnnouncements) {
            Question topQuestion = queryFactory
                    .selectFrom(question)
                    .leftJoin(heart).on(heart.question.eq(question))
                    .where(question.announcement.eq(announcement))
                    .groupBy(question.id)
                    .orderBy(heartsCount.desc())
                    .limit(1)
                    .fetchOne();
            if (topQuestion != null) {
                topQuestions.add(topQuestion);
            }
        }
        return topQuestions;
    }

    @Override
    public List<Question> findQuestionWaitingAnswerOnOff(Long userId, University university) {

        // userId가 roadmap에 있는지 확인하여 정렬 가중치 계산
        NumberExpression<Integer> userRoadmapPriority = new CaseBuilder()
                .when(roadmapAnnouncement.roadmap.user.id.eq(userId)).then(1)
                .otherwise(0);

        // 공고에 대한 로드맵 저장 횟수를 계산
        NumberExpression<Long> roadmapCount = roadmapAnnouncement.count();
        // heart 개수를 계산하는 집계
        NumberExpression<Integer> heartsCount = new CaseBuilder()
                .when(heart.heartType.eq(HeartType.QUESTION))
                .then(1)
                .otherwise(0)
                .sum();

        // ON_CAMPUS 공고 선택
        Announcement onCampusAnnouncement = queryFactory
                .selectFrom(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement))
                .leftJoin(question).on(question.announcement.eq(announcement))
                .leftJoin(heart).on(heart.question.eq(question))
                .where(announcement.announcementType.eq(AnnouncementType.ON_CAMPUS),
                        announcement.university.eq(university))
                .groupBy(announcement.id)
                .orderBy(
                        userRoadmapPriority.desc(),
                        roadmapCount.desc(),
                        heartsCount.desc(),
                        announcement.createdAt.desc()
                )
                .limit(1)
                .fetchOne();

        // BIZ_INFO 또는 OPEN_DATA 공고 선택
        Announcement bizOrOpenDataAnnouncement = queryFactory
                .selectFrom(announcement)
                .leftJoin(roadmapAnnouncement).on(roadmapAnnouncement.announcement.eq(announcement))
                .leftJoin(question).on(question.announcement.eq(announcement))
                .leftJoin(heart).on(heart.question.eq(question))
                .where(announcement.announcementType.in(AnnouncementType.BIZ_INFO, AnnouncementType.OPEN_DATA))
                .groupBy(announcement.id)
                .orderBy(
                        userRoadmapPriority.desc(),
                        roadmapCount.desc(),
                        heartsCount.desc(),
                        announcement.createdAt.desc()
                )
                .limit(1)
                .fetchOne();

        // 선택된 공고 리스트
        List<Announcement> selectedAnnouncements = Arrays.asList(onCampusAnnouncement, bizOrOpenDataAnnouncement);

        // 각 선택된 공고에 대해 하트가 가장 많은 질문 조회
        List<Question> topQuestions = new ArrayList<>();
        for (Announcement announcement : selectedAnnouncements) {
            if (announcement != null) {  // Null 체크 추가
                Question topQuestion = queryFactory
                        .selectFrom(question)
                        .leftJoin(heart).on(heart.question.eq(question))
                        .where(question.announcement.eq(announcement))
                        .groupBy(question.id)
                        .orderBy(heartsCount.desc())
                        .limit(1)
                        .fetchOne();
                if (topQuestion != null) {
                    topQuestions.add(topQuestion);
                }
            }
        }

        return topQuestions;
    }
}
