package com.startingblock.domain.heart.domain.repository;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.heart.domain.Heart;
import com.startingblock.domain.heart.domain.HeartType;
import com.startingblock.domain.reply.domain.Reply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HeartRepository extends JpaRepository<Heart, Long> {
    List<Heart> findAllByAnswer(final Answer answer);

    List<Heart> findAllByReply(final Reply reply);

    Integer countByQuestionId(Long questionId);

    Boolean existsByQuestionIdAndUserId(Long questionId, Long userId);

    Optional<Heart> findByUserIdAndQuestionId(Long userId, Long questionId);

    List<Heart> findAllByUserIdAndHeartType(Long userId, HeartType heartType);
}
