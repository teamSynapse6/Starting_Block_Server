package com.startingblock.domain.answer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public class AnswerRequestDto {

    @Getter
    public static class AnswerRequest {
        @Schema(type = "long", example = "1", description = "질문의 ID입니다.")
        private Long questionId;

        @Schema(type = "String", example = "저번에 참가했을 땐 ..", description = "답변의 내용입니다.")
        private String content;

        @Schema(type = "Boolean", example = "true", description = "문의처 답변 여부입니다.")
        private Boolean isContact;
    }
}
