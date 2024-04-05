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

    // TODO: 답변 달기 (사용자)
    @Transactional
    public void sendGeneralAnswer(final UserPrincipal userPrincipal, final AnswerRequestDto.AnswerRequest dto) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        Question question = questionRepository.findById(dto.getQuestionId())
                .orElseThrow(InvalidQuestionException::new);

        Answer answer = Answer.builder()
                .content(dto.getContent())
                .user(user)
                .question(question)
                .answerType(QAType.GENERAL)
                .build();
        answerRepository.save(answer);
    }

    // TODO: 답변 달기 (담당자)
    @Transactional
    public void sendContactAnswer(final AnswerRequestDto.AnswerRequest dto) {
        createContactAnswer(dto);
    }

    // TODO: 모든 답변 완료 (담당자)
    @Transactional
    public void sendContactAnswerAll(final AnswerRequestDto.AnswerListRequest dto) {
        for (AnswerRequestDto.AnswerRequest answerRequest : dto.getQuestions()) {
            createContactAnswer(answerRequest);
        }
    }

    @Transactional
    public void createContactAnswer(final AnswerRequestDto.AnswerRequest dto) {
        Question question = questionRepository.findById(dto.getQuestionId())
                .orElseThrow(InvalidQuestionException::new);
        // 문의처 질문이 아닌 경우 조기 리턴
        if (question.getQuestionType() != QAType.CONTACT){
            return;
        }
        // 답변 완료 / 다음에 답하기 모두 isNew를 false로 바꿔준다.
        question.changeIsNew();
        // 답변 완료
        if (dto.getContent() != null) {
            // 담당자의 답변이 달리면 (!= null) question의 isAnswerd를 true로 바꿔준다.
            question.changeIsAnswerd();
            Answer answer = Answer.builder()
                    .content(dto.getContent())
                    .user(null) // 담당자는 null
                    .question(question)
                    .answerType(QAType.CONTACT)
                    .build();
            answerRepository.save(answer);
        }
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
