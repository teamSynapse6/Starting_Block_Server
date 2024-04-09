package com.startingblock.domain.heart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

public class HeartResponseDto {

    @Builder
    @Getter
    public static class MyHeartResponse {

        @Schema(type = "String", example = "교외", description = "공고 구분입니다.")
        private String announcementType;

        @Schema(type = "String", example = "청년 취창업 멘토링", description = "공고 이름입니다.")
        private String announcementName;

        @Schema(type = "Long", example = "1", description = "질문 ID입니다.")
        private Long questionId;

        @Schema(type = "String", example = "개별 멘토링 진행 시..", description = "질문 내용입니다.")
        private String questionContent;

        @Schema(type = "Integer", example = "16", description = "댓글 + 대댓글 합산 수 입니다.")
        private Integer answerCount;
    }
}
