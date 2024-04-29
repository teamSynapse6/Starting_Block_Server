package com.startingblock.domain.heart.application;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.exception.InvalidAnswerException;
import com.startingblock.domain.heart.domain.Heart;
import com.startingblock.domain.heart.domain.repository.HeartRepository;
import com.startingblock.domain.heart.dto.HeartRequestDto;
import com.startingblock.domain.heart.exception.InvalidHeartException;
import com.startingblock.domain.question.domain.Question;
import com.startingblock.domain.question.domain.repository.QuestionRepository;
import com.startingblock.domain.question.exception.InvalidQuestionException;
import com.startingblock.domain.reply.domain.Reply;
import com.startingblock.domain.reply.domain.repository.ReplyRepository;
import com.startingblock.domain.reply.exception.InvalidReplyException;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartService {

    private final HeartRepository heartRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ReplyRepository replyRepository;

    // TODO: 하트 누르기
    @Transactional
    public void send(final UserPrincipal userPrincipal, final HeartRequestDto.HeartRequest dto) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        Heart heart = Heart.builder()
                .heartType(dto.getHeartType())
                .user(user)
                .build();

        // 하트의 타입에 따라서 질문/답변/답글 유효성 체크
        switch (dto.getHeartType()) {
            case QUESTION -> {
                Question question = questionRepository.findById(dto.getId())
                        .orElseThrow(InvalidQuestionException::new);
                heart.updateQuestion(question);
            }
            case ANSWER -> {
                Answer answer = answerRepository.findById(dto.getId())
                        .orElseThrow(InvalidAnswerException::new);
                heart.updateAnswer(answer);
            }
            case REPLY -> {
                Reply reply = replyRepository.findById(dto.getId())
                        .orElseThrow(InvalidReplyException::new);
                heart.updateReply(reply);
            }
        }

        heartRepository.save(heart);
    }

    // TODO: 하트 취소
    @Transactional
    public void cancel(final UserPrincipal userPrincipal, final Long heartId) {
        userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        Heart heart = heartRepository.findById(heartId)
                .orElseThrow(InvalidHeartException::new);
        heartRepository.delete(heart);
    }
}
