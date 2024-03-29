package com.startingblock.domain.reply.domain.repository;

import com.startingblock.domain.answer.domain.Answer;
import com.startingblock.domain.reply.domain.Reply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReplyRepository extends JpaRepository<Reply, Long>, ReplyQuerydslRepository{

    List<Reply> findAllByAnswer(final Answer answer);
}
