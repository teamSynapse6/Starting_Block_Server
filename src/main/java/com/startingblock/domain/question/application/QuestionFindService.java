package com.startingblock.domain.question.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.heart.domain.Heart;
import com.startingblock.domain.heart.domain.repository.HeartRepository;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.dto.QuestionResponseDto;
import com.startingblock.domain.question.exception.InvalidQuestionException;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionFindService {

    private final QuestionRepository questionRepository;
    private final AnnouncementRepository announcementRepository;
    private final AnswerRepository answerRepository;
    private final HeartRepository heartRepository;

    // TODO: 질문 리스트 조회
    public List<QuestionResponseDto.QuestionListResponse> findByAnnouncement(final UserPrincipal userPrincipal, final Long announcementId) {
        announcementRepository.findById(announcementId)
                .orElseThrow(InvalidAnnouncementException::new);
        return questionRepository.findQuestionListByAnnouncementId(userPrincipal.getId(), announcementId);
    }

    // TODO: 나의 질문 조회

    // TODO: 질문 상세 조회
    public QuestionResponseDto.QuestionDetailResponse findDetail(final UserPrincipal userPrincipal, final Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(InvalidQuestionException::new);
        Optional<Heart> heart = heartRepository.findByUserIdAndQuestionId(userPrincipal.getId(), questionId);

        return QuestionResponseDto.QuestionDetailResponse.builder()
                .userName(question.getUser().getName())
                .content(question.getContent())
                .createdAt(question.getCreatedAt())
                .heartCount(heartRepository.countByQuestionId(questionId))
                .isMyHeart(heartRepository.existsByQuestionIdAndUserId(questionId, userPrincipal.getId()))
                .heartId(heart.isPresent() ? heart.get().getId() : null)
                .contactAnswer(answerRepository.findContactAnswer(questionId))
                .answerCount(answerRepository.countByQuestionIdAndAnswerType(questionId, QAType.GENERAL))
                .answerList(answerRepository.findAnswerList(questionId, userPrincipal.getId()))
                .build();
    }

    // TODO: 웹 - 공고별 질문 리스트 조회
    public QuestionResponseDto.QuestionListResponseForWeb findByAnnouncementForWeb(final Long announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(InvalidAnnouncementException::new);

        List<Question> oldQuestionList = questionRepository.findByAnnouncementIdAndQuestionTypeAndIsAnswerdAndIsNew(announcementId, QAType.CONTACT, false, false);
        List<Question> newQuestionList = questionRepository.findByAnnouncementIdAndQuestionTypeAndIsAnswerdAndIsNew(announcementId, QAType.CONTACT, false, true);
        List<QuestionResponseDto.QuestionSimpleResponse> oldQuestions = new ArrayList<>();
        List<QuestionResponseDto.QuestionSimpleResponse> newQuestions = new ArrayList<>();

        for (Question question : oldQuestionList) {
            oldQuestions.add(QuestionResponseDto.QuestionSimpleResponse.builder()
                    .questionId(question.getId())
                    .content(question.getContent())
                    .build());
        }
        for (Question question : newQuestionList) {
            newQuestions.add(QuestionResponseDto.QuestionSimpleResponse.builder()
                    .questionId(question.getId())
                    .content(question.getContent())
                    .build());
        }
        return QuestionResponseDto.QuestionListResponseForWeb.builder()
                .announcementName(announcement.getTitle())
                .detailUrl(announcement.getDetailUrl())
                .oldQuestions(oldQuestions)
                .newQuestions(newQuestions)
                .build();
    }
}
