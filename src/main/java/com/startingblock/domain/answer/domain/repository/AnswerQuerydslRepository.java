package com.startingblock.domain.answer.domain.repository;

import com.startingblock.domain.answer.dto.AnswerResponseDto;

import java.util.List;

public interface AnswerQuerydslRepository {

    AnswerResponseDto.ContactAnswerResponse findContactAnswer(Long questionId);

    List<AnswerResponseDto.AnswerListResponse> findAnswerList(Long questionId, Long userId);
}
