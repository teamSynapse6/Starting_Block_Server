package com.startingblock.domain.reply.domain.repository;

import com.startingblock.domain.reply.dto.ReplyResponseDto;

import java.util.List;

public interface ReplyQuerydslRepository {

    List<ReplyResponseDto.ReplyResponse> findReplyListByAnswerId(Long answerId, Long userId);
}
