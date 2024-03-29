package com.startingblock.domain.heart.domain.repository;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.heart.domain.Heart;
import com.startingblock.domain.reply.domain.Reply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeartRepository extends JpaRepository<Heart, Long> {
    List<Heart> findAllByAnswer(final Answer answer);

    List<Heart> findAllByReply(final Reply reply);

    Integer countByQuestionId(Long questionId);

    Boolean existsByQuestionIdAndUserId(Long questionId, Long userId);
}
