package com.startingblock.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationSendRes(
        @JsonProperty("requested_token_count")
        int requestedTokenCount,
        @JsonProperty("success_count")
        int successCount,
        @JsonProperty("failure_count")
        int failureCount,
        boolean skipped
) {
}
