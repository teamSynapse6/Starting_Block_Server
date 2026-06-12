package com.startingblock.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class AppleSignInReq {

    @Schema(type = "String", description = "Apple authorization code")
    private String authorizationCode;

    @Schema(type = "String", description = "Apple identity token JWT")
    private String identityToken;

    @Schema(type = "String", description = "Apple user identifier")
    private String providerId;

    @Schema(type = "String", description = "Apple user identifier")
    private String userIdentifier;

    @Schema(type = "String", description = "Apple account email")
    private String email;

    @Schema(type = "String", description = "Apple user given name")
    private String givenName;

    @Schema(type = "String", description = "Apple user family name")
    private String familyName;
}
