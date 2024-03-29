package com.startingblock.domain.reply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public class ReplyRequestDto {

    @Getter
    public static class ReplyRequest {

        @Schema(type = "long", example = "1", description = "답변의 ID입니다.")
        private Long answerId;

        @Schema(type = "String", example = "도움됐어요!", description = "답글의 내용입니다.")
        private String content;
    }
}
