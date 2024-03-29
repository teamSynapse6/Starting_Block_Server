package com.startingblock.domain.reply.domain.repository;

import com.startingblock.domain.reply.domain.Reply;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyRepository extends JpaRepository<Reply, Long> {
}
