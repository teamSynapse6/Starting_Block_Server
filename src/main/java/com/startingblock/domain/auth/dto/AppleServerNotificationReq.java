package com.startingblock.domain.auth.dto;

import lombok.Getter;

@Getter
public class AppleServerNotificationReq {
    private String payload;
    private String signedPayload;

    public String getPayload() {
        return payload != null ? payload : signedPayload;
    }
}
