package com.startingblock.domain.answer.application;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.dto.AnswerRequestDto;
import com.startingblock.domain.answer.exception.InvalidAnswerException;
import com.startingblock.domain.heart.domain.Heart;
import com.startingblock.domain.heart.domain.repository.HeartRepository;
import com.startingblock.domain.question.domain.QAType;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.exception.InvalidQuestionException;
import com.startingblock.domain.reply.domain.Reply;
import com.startingblock.domain.reply.domain.repository.ReplyRepository;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.DefaultAssert;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final HeartRepository heartRepository;
    private final ReplyRepository replyRepository;

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

    // TODO: 답변 삭제
    @Transactional
    public void cancel(final UserPrincipal userPrincipal, final Long answerId) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        Answer answer = answerRepository.findById(answerId)
                .orElseThrow(InvalidAnswerException::new);
        DefaultAssert.isTrue(answer.getUser().equals(user), "나의 답변만 삭제할 수 있습니다.");
        // 답글 삭제
        List<Reply> replys = replyRepository.findAllByAnswer(answer);
        replyRepository.deleteAll(replys);
        // 하트 삭제
        List<Heart> hearts = heartRepository.findAllByAnswer(answer);
        heartRepository.deleteAll(hearts);
        // 답변 삭제
        answerRepository.delete(answer);
    }
}
