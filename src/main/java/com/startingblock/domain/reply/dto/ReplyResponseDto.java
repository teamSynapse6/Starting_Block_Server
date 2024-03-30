package com.startingblock.domain.reply.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

public class ReplyResponseDto {

    @Getter
    public static class ReplyResponse {

        @Schema(type = "Long", example = "1", description = "답글의 ID입니다.")
        private Long replyId;

        @Schema(type = "String", example = "예비 창업자", description = "답글을 남긴 유저의 이름입니다.")
        private String userName;

        @Schema(type = "String", example = "답글을 달겠습니다.", description = "답글의 내용입니다.")
        private String content;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "답글 생성일자입니다.")
        private LocalDateTime createdAt;

        @Schema(type = "Integer", example = "16", description = "좋아요의 개수입니다.")
        private Integer heartCount;

        @Schema(type = "Boolean", example = "true", description = "내가 하트를 눌렀는지 여부입니다.")
        private Boolean isMyHeart;

        @Schema(type = "Long", example = "1", description = "내 답글 하트의 ID입니다.", nullable = true)
        private Long heartId;

        public ReplyResponse(final Long replyId, final String userName, final String content, final LocalDateTime createdAt, final Long heartCount, final Boolean isMyHeart, final Long heartId) {
            this.replyId = replyId;
            this.userName = userName;
            this.content = content;
            this.createdAt = createdAt;
            this.heartCount = heartCount != null ? heartCount.intValue() : 0;
            this.isMyHeart = isMyHeart;
            this.heartId = heartId;
        }
    }
}
