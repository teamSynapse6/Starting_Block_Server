package com.startingblock.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record NotificationSendReq(
        @JsonProperty("user_id")
        @NotNull Long userId,
        @NotBlank String title,
        @NotBlank String body,
        Map<String, String> data
) {
}
