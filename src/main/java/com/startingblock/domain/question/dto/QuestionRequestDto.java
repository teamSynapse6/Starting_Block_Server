package com.startingblock.domain.question.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public class QuestionRequestDto {

    @Getter
    public static class AskQuestionRequest {
        @Schema(type = "long", example = "1", description = "공고의 ID입니다.")
        private Long announcementId;

        @Schema(type = "String", example = "개별 멘토링 진행시..", description = "질문의 내용입니다.")
        private String content;

        @Schema(type = "boolean", example = "true", description = "문의처 질문 여부입니다.")
        private Boolean isContact;
    }
}
