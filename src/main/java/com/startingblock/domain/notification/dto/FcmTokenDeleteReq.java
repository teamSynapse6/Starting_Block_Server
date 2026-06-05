package com.startingblock.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmTokenDeleteReq(@NotBlank String token) {
}
