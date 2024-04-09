package com.startingblock.domain.question.domain.repository;

import com.startingblock.domain.question.dto.QuestionResponseDto;

import java.util.List;

public interface QuestionQuerydslRepository {

    List<QuestionResponseDto.QuestionListResponse> findQuestionListByAnnouncementId(Long userId, Long announcementId);

    List<QuestionResponseDto.QuestionByMyAnswerAndReply> findQuestionByMyAnswerAndReply(Long userId);
}
