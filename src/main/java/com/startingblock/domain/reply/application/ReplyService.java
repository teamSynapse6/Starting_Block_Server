package com.startingblock.domain.reply.application;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.answer.domain.repository.AnswerRepository;
import com.startingblock.domain.answer.exception.InvalidAnswerException;
import com.startingblock.domain.reply.domain.Reply;
import com.startingblock.domain.reply.domain.repository.ReplyRepository;
import com.startingblock.domain.reply.dto.ReplyRequestDto;
import com.startingblock.domain.reply.exception.InvalidReplyException;
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
public class ReplyService {

    private final ReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final AnswerRepository answerRepository;

    // TODO: 답글 쓰기
    @Transactional
    public void send(final UserPrincipal userPrincipal, final ReplyRequestDto.ReplyRequest dto) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        Answer answer = answerRepository.findById(dto.getAnswerId())
                .orElseThrow(InvalidAnswerException::new);
        Reply reply = Reply.builder()
                .content(dto.getContent())
                .user(user)
                .answer(answer)
                .build();

        replyRepository.save(reply);
    }

    // TODO: 답글 삭제
    @Transactional
    public void cancel(final UserPrincipal userPrincipal, final Long replyId) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        Reply reply = replyRepository.findById(replyId)
                .orElseThrow(InvalidReplyException::new);
        DefaultAssert.isTrue(reply.getUser().equals(user), "나의 댓글만 삭제할 수 있습니다.");

        replyRepository.delete(reply);
    }
}
