package com.startingblock.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenRegisterReq(
        @NotBlank String token,
        String platform,
        String deviceId
) {
}
