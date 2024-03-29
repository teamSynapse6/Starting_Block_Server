package com.startingblock.domain.question.dto;

import com.startingblock.domain.answer.dto.AnswerResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class QuestionResponseDto {

    @Getter
    public static class QuestionListResponse {

        @Schema(type = "Long", example = "1", description = "질문의 ID입니다.")
        private Long questionId;

        @Schema(type = "String", example = "개별 멘토링 진행시..", description = "질문의 내용입니다.")
        private String content;

        @Schema(type = "Integer", example = "16", description = "궁금해요의 개수입니다.")
        private Integer heartCount;

        @Schema(type = "Integer", example = "6", description = "답변의 개수입니다.")
        private Integer answerCount;

        @Schema(type = "Boolean", example = "true", description = "내가 하트를 눌렀는지 여부입니다.")
        private Boolean isMyHeart;

        public QuestionListResponse(final Long questionId, final String content, final Long heartCount, final Long answerCount, final Boolean isMyHeart) {
            this.questionId = questionId;
            this.content = content;
            this.heartCount = heartCount != null ? heartCount.intValue() : 0;
            this.answerCount = answerCount != null ? answerCount.intValue() : 0;
            this.isMyHeart = isMyHeart;
        }
    }

    @Getter
    @Builder
    public static class QuestionDetailResponse {

        @Schema(type = "String", example = "예비 창업자", description = "질문을 남긴 유저의 이름입니다.")
        private String userName;

        @Schema(type = "String", example = "개별 멘토링 진행시..", description = "질문의 내용입니다.")
        private String content;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "질문 생성일자입니다.")
        private LocalDateTime createdAt;

        @Schema(type = "Boolean", example = "true", description = "내가 하트를 눌렀는지 여부입니다.")
        private Boolean isMyHeart;

        @Schema(type = "Integer", example = "16", description = "궁금해요의 개수입니다.")
        private Integer heartCount;

        private AnswerResponseDto.ContactAnswerResponse contactAnswer;

        @Schema(type = "Integer", example = "3", description = "타 창업자의 답변 개수입니다.")
        private Integer answerCount;

        private List<AnswerResponseDto.AnswerListResponse> answerList;
    }
}
