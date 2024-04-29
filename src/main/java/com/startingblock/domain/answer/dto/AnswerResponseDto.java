package com.startingblock.domain.answer.dto;

import com.startingblock.domain.reply.dto.ReplyResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
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

        @Schema(type = "Boolean", example = "true", description = "나의 답변인지 여부입니다.")
        private Boolean isMyAnswer;

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

        @Schema(type = "Long", example = "1", description = "내 답변 하트의 ID입니다.", nullable = true)
        private Long heartId;

        @Setter
        private List<ReplyResponseDto.ReplyResponse> replyResponse;

        public AnswerListResponse(final Long answerId, final Boolean isMyAnswer, final String userName, final String content, final LocalDateTime createdAt, final Long heartCount, final Boolean isMyHeart, final Long heartId) {
            this.answerId = answerId;
            this.isMyAnswer = isMyAnswer;
            this.userName = userName;
            this.content = content;
            this.createdAt = createdAt;
            this.heartCount = heartCount != null ? heartCount.intValue() : 0;
            this.isMyHeart = isMyHeart;
            this.heartId = heartId;
        }
    }

    @Getter
    @Builder
    public static class MyWriteResponse {

        @Schema(type = "String", example = "교외", description = "공고 타입입니다.")
        String announcementType;

        @Schema(type = "String", example = "예비 창업 패키지", description = "공고 이름입니다.")
        String announcementName;

        @Schema(type = "int", example = "1", description = "질문 작성자 프로필 아이콘입니다.")
        int questionWrtierProfile;

        @Schema(type = "String", example = "제이콥", description = "질문 작성자 닉네임입니다.")
        String questionWriterName;

        @Schema(type = "String", example = "멘토링 일정은 어떻게 되나요?", description = "질문 내용입니다..")
        String questionContent;

        // 내 답변 / 내 답글 둘 중 하나는 null 입니다.
        @Setter
        MyAnswerResponse myAnswer;

        @Setter
        MyReplyResponse myReply;
    }

    @Getter
    @Builder
    public static class MyAnswerResponse{

        @Schema(type = "String", example = "멘토링 일정은..", description = "답변 내용입니다.")
        String answerContent;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "답변 작성 일자입니다.")
        LocalDateTime createdAt;

        @Schema(type = "int", example = "4", description = "답변 도움(좋아요) 누적 수 입니다.")
        int heartCount;

        @Schema(type = "int", example = "16", description = "답변 + 답글 누적 수 입니다.")
        int replyCount;
    }

    @Getter
    @Builder
    public static class MyReplyResponse{

        @Schema(type = "int", example = "1", description = "답변 작성자 프로필 아이콘입니다.")
        int answerWriterProfile;

        @Schema(type = "String", example = "제이콥", description = "답변 작성자 닉네임입니다.")
        String answerWriterName;

        @Schema(type = "String", example = "멘토링 일정은..", description = "답변 내용입니다.")
        String answerContent;

        List<ReplyList> replyList;
    }

    @Getter
    @Builder
    public static class ReplyList{

        @Schema(type = "boolean", example = "true", description = "내 답글인지, 타 유저의 답글인지 여부입니다.")
        boolean isMine;

        @Schema(type = "int", example = "1", description = "답글 작성자 프로필 아이콘입니다.")
        int replyWriterProfile;

        @Schema(type = "String", example = "제이콥", description = "답글 작성자 닉네임입니다.")
        String replyWriterName;

        @Schema(type = "String", example = "멘토링 일정은..", description = "답글 내용입니다.")
        String replyContent;

        @Schema(type = "LocalDateTime", example = "2024-03-29 18:26:35.337633", description = "답글 작성 일자입니다.")
        LocalDateTime createdAt;

        @Schema(type = "int", example = "4", description = "답글 도움(좋아요) 누적 수 입니다.")
        int heartCount;
    }
}
