package com.startingblock.domain.reply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

public class ReplyResponseDto {

    @Getter
    @AllArgsConstructor
    public static class ReplyResponse {

        @Schema(type = "String", example = "예비 창업자", description = "답글을 남긴 유저의 이름입니다.")
        private String userName;

        @Schema(type = "String", example = "답글을 달겠습니다.", description = "답글의 내용입니다.")
        private String content;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "답글 생성일자입니다.")
        private LocalDateTime createdAt;

        @Schema(type = "Boolean", example = "true", description = "내가 하트를 눌렀는지 여부입니다.")
        private Boolean isMyHeart;
    }
}
