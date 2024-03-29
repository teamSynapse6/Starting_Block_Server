package com.startingblock.domain.question.domain.repository;

import com.startingblock.domain.question.dto.QuestionResponseDto;

import java.util.List;

public interface QuestionQuerydslRepository {

    List<QuestionResponseDto.QuestionListResponse> findQuestionListByAnnouncementId(Long announcementId);
}
