package com.startingblock.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Data
public class SignInReq {

    @Schema(type = "String", description = "ProviderId")
    private String providerId;

    @Schema(type = "String", description = "Email")
    private String email;

}
