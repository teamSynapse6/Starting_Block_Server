package com.startingblock.domain.answer.application;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.dto.AnswerRequestDto;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.exception.InvalidQuestionException;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.DefaultAssert;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;

    // TODO: 답변 달기
    @Transactional
    public void send(final UserPrincipal userPrincipal, final AnswerRequestDto.AnswerRequest dto) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        Question question = questionRepository.findById(dto.getQuestionId())
                .orElseThrow(InvalidQuestionException::new);

        // 답변의 isContact가 true인 경우, 질문의 타입이 CONTACT 여야함.
        if (dto.getIsContact()) {
            DefaultAssert.isTrue(question.getQuestionType() == QAType.CONTACT, "문의처 질문이 아닙니다.");
        }

        Answer answer = Answer.builder()
                .content(dto.getContent())
                .user(user)
                .question(question)
                .answerType(dto.getIsContact() ? QAType.CONTACT : QAType.GENERAL)
                .build();

        if (dto.getIsContact()) {
            question.changeIsAnswerd();
        }
        answerRepository.save(answer);
    }
}
