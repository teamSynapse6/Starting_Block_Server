package com.startingblock.domain.answer.dto;

import com.startingblock.domain.reply.dto.ReplyResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class AnswerResponseDto {

    @Getter
    @AllArgsConstructor
    public static class ContactAnswerResponse {

        @Schema(type = "Long", example = "1", description = "답변의 ID입니다.")
        private Long answerId;

        @Schema(type = "String", example = "송파구청 일자리정책담당관", description = "담당자 정보입니다.")
        private String organizationManger;

        @Schema(type = "String", example = "답변 드리겠습니다.", description = "문의처 담당자의 답변 내용입니다.")
        private String content;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "문의처 답변 생성일자입니다.")
        private LocalDateTime createdAt;
    }

    @Getter
    public static class AnswerListResponse {

        @Schema(type = "Long", example = "1", description = "답변의 ID입니다.")
        private Long answerId;

        @Schema(type = "String", example = "토들러", description = "답변을 남긴 유저의 이름입니다.")
        private String userName;

        @Schema(type = "String", example = "답변 드리겠습니다.", description = "타 창업자의 답변 내용입니다.")
        private String content;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "타 창업자 답변 생성일자입니다.")
        private LocalDateTime createdAt;

        @Schema(type = "Integer", example = "2", description = "도움이 됐어요 개수입니다.")
        private Integer heartCount;

        @Schema(type = "Boolean", example = "true", description = "내가 하트를 눌렀는지 여부입니다.")
        private Boolean isMyHeart;

        @Setter
        private List<ReplyResponseDto.ReplyResponse> replyResponse;

        public AnswerListResponse(final Long answerId, final String userName, final String content, final LocalDateTime createdAt, final Long heartCount, final Boolean isMyHeart) {
            this.answerId = answerId;
            this.userName = userName;
            this.content = content;
            this.createdAt = createdAt;
            this.heartCount = heartCount != null ? heartCount.intValue() : 0;
            this.isMyHeart = isMyHeart;
        }
    }
}
