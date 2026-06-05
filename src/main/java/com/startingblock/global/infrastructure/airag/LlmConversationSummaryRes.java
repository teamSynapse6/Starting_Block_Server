package com.startingblock.global.infrastructure.airag;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record LlmConversationSummaryRes(
        @JsonProperty("thread_id")
        String threadId,
        @JsonProperty("announcement_id")
        Long announcementId,
        String title,
        @JsonProperty("last_message")
        String lastMessage,
        @JsonProperty("last_message_created_at")
        LocalDateTime lastMessageCreatedAt
) {
}
