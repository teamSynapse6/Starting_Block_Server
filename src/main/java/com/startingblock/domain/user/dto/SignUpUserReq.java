package com.startingblock.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SignUpUserReq {

    @Schema(type = "string", example = "Jin", description = "닉네임")
    private String nickname;
    @Schema(type = "string", example = "1996-01-01", description = "생년월일")
    private LocalDate birth;
    @Schema(type = "boolean", example = "true", description = "사업자등록여부")
    private Boolean isCompletedBusinessRegistration;
    @Schema(type = "string", example = "서울", description = "거주지")
    private String residence;
    @Schema(type = "string", example = "서울대학교", description = "대학교")
    private String university;

}
