package com.startingblock.domain.question.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.answer.dto.AnswerResponseDto;
import com.startingblock.domain.gpt.dto.GroupingQuestionRes;
import com.startingblock.domain.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

public class QuestionResponseDto {

    @Getter
    public static class QuestionListResponse {

        @Schema(type = "Long", example = "1", description = "질문의 ID입니다.")
        private Long questionId;

        @Schema(type = "String", example = "개별 멘토링 진행시..", description = "질문의 내용입니다.")
        private String content;

        @Schema(type = "Integer", example = "6", description = "답변의 개수입니다.")
        private Integer answerCount;

        @Schema(type = "Boolean", example = "true", description = "담당처 답변이 달렸는지 여부입니다.")
        private Boolean isHaveContactAnswer;

        @Schema(type = "Integer", example = "16", description = "궁금해요의 개수입니다.")
        private Integer heartCount;

        @Schema(type = "Boolean", example = "true", description = "내가 하트를 눌렀는지 여부입니다.")
        private Boolean isMyHeart;

        @Schema(type = "Long", example = "1", description = "내 질문 하트의 ID입니다.", nullable = true)
        private Long heartId;

        public QuestionListResponse(final Long questionId, final String content, final Long answerCount, final Boolean isHaveContactAnswer,
                                    final Long heartCount, final Boolean isMyHeart, final Long heartId) {
            this.questionId = questionId;
            this.content = content;
            this.answerCount = answerCount != null ? answerCount.intValue() : 0;
            this.isHaveContactAnswer = isHaveContactAnswer;
            this.heartCount = heartCount != null ? heartCount.intValue() : 0;
            this.isMyHeart = isMyHeart;
            this.heartId = heartId;
        }
    }

    @Getter
    @Builder
    public static class QuestionDetailResponse {

        @Schema(type = "String", example = "예비 창업자", description = "질문을 남긴 유저의 이름입니다.")
        private String userName;

        @Schema(type = "Integer", example = "1", description = "유저의 프로필 번호입니다.")
        private Integer profileNumber;

        @Schema(type = "String", example = "개별 멘토링 진행시..", description = "질문의 내용입니다.")
        private String content;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "질문 생성일자입니다.")
        private LocalDateTime createdAt;

        @Schema(type = "Integer", example = "16", description = "궁금해요의 개수입니다.")
        private Integer heartCount;

        @Schema(type = "Boolean", example = "true", description = "내가 하트를 눌렀는지 여부입니다.")
        private Boolean isMyHeart;

        @Schema(type = "Long", example = "1", description = "내 질문 하트의 ID입니다.", nullable = true)
        private Long heartId;

        private AnswerResponseDto.ContactAnswerResponse contactAnswer;

        @Schema(type = "Integer", example = "3", description = "타 창업자의 답변 개수입니다.")
        private Integer answerCount;

        private List<AnswerResponseDto.AnswerListResponse> answerList;
    }

    @Getter
    @Builder
    public static class QuestionListResponseForWeb {

        @Schema(type = "String", example = "예비 창업 패키지", description = "공고의 이름입니다.")
        private String announcementName;

        @Schema(type = "String", example = "www.naver.com", description = "공고의 상세 주소입니다.")
        private String detailUrl;

        private List<GroupingQuestionRes> oldQuestions;

        private List<GroupingQuestionRes> newQuestions;
    }

    @Getter
    @Builder
    public static class QuestionSimpleResponse {

        @Schema(type = "Long", example = "1", description = "질문의 ID입니다.")
        private Long questionId;

        @Schema(type = "String", example = "개별 멘토링 진행시..", description = "질문의 내용입니다.")
        private String content;
    }

    @Getter
    @Builder
    public static class MyQuestionListResponse {

        @Schema(type = "Long", example = "1", description = "공고 ID입니다.")
        private Long announcementId;

        @Schema(type = "String", example = "교외", description = "공고 구분입니다.")
        private String announcementType;

        @Schema(type = "String", example = "청년 취창업 멘토링", description = "공고 이름입니다.")
        private String announcementName;

        @Schema(type = "Long", example = "1", description = "질문 ID입니다.")
        private Long questionId;

        @Schema(type = "String", example = "개별 멘토링 진행 시..", description = "질문 내용입니다.")
        private String questionContent;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "질문 생성일자입니다.")
        private LocalDateTime createdAt;

        @Schema(type = "Integer", example = "16", description = "질문 도움(좋아요) 누적 수 입니다.")
        private Integer heartCount;

        @Schema(type = "Integer", example = "16", description = "댓글 + 대댓글 합산 수 입니다.")
        private Integer answerCount;

        @Schema(type = "String", example = "송파구청 일자리정책담당관", description = "담당자 정보입니다.")
        private String organizationManger;

        @Schema(type = "String", example = "답변 드리겠습니다.", description = "문의처 담당자의 답변 내용입니다.")
        private String contactAnswerContent;
    }

    @Getter
    public static class QuestionByMyAnswerAndReply {
        private Announcement announcement;
        private User user;
        private Long questionId;
        private String content;
        private Long answerOrReplyId;
        @Setter
        private String writeType;

        public QuestionByMyAnswerAndReply(final Announcement announcement, final User user, final Long questionId, final String content, final Long answerOrReplyId) {
            this.announcement = announcement;
            this.user = user;
            this.questionId = questionId;
            this.content = content;
            this.answerOrReplyId = answerOrReplyId;
        }
    }

    @Getter
    @Builder
    public static class QuestionResponseForStatusCheck {
        private String title;
        private Long questionId;
        private String content;
        private LocalDateTime receptionTime;
        private LocalDateTime sendTime;
        private LocalDateTime arriveTime;
    }

    @Getter
    @Builder
    public static class WaitingAnswer {
        private Long announcementId;
        private String announcementType;
        private String announcementTitle;
        private Long questionId;
        private String questionContent;
        private Integer heartCount;
        private LocalDateTime createdAt;
    }
}
