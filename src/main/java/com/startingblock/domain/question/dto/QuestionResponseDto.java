package com.startingblock.domain.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

public class QuestionResponseDto {

    @Builder
    @Getter
    public static class QuestionListResponse {
        @Schema(type = "String", example = "개별 멘토링 진행시..", description = "질문의 내용입니다.")
        private String content;

        @Schema(type = "Integer", example = "6", description = "답변의 개수입니다.")
        private Integer answerCount;

        @Schema(type = "Integer", example = "16", description = "궁금해요의 개수입니다.")
        private Integer heartCount;
    }
}
