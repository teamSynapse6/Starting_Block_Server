package com.startingblock.domain.question.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.announcement.exception.InvalidAnnouncementException;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.dto.AnswerResponseDto;
import com.startingblock.domain.heart.domain.repository.HeartRepository;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.dto.QuestionResponseDto;
import com.startingblock.domain.question.exception.InvalidQuestionException;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionFindService {

    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
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

        return QuestionResponseDto.QuestionDetailResponse.builder()
                .userName(question.getUser().getName())
                .content(question.getContent())
                .createdAt(question.getCreatedAt())
                .isMyHeart(heartRepository.existsByQuestionIdAndUserId(questionId, userPrincipal.getId()))
                .heartCount(heartRepository.countByQuestionId(questionId))
                .contactAnswer(answerRepository.findContactAnswer(questionId))
                .answerCount(answerRepository.countByQuestionIdAndAnswerType(questionId, QAType.GENERAL))
                .answerList(answerRepository.findAnswerList(questionId, userPrincipal.getId()))
                .build();
    }
}
