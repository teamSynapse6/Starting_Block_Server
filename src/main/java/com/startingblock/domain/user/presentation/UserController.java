package com.startingblock.domain.user.presentation;

import com.startingblock.domain.user.application.UserService;
import com.startingblock.domain.user.dto.SignUpUserReq;
import com.startingblock.domain.user.dto.UserDto;
import com.startingblock.global.config.security.token.CurrentUser;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.payload.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "Users API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "현재 유저 정보 조회", description = "현재 유저 정보 조회 합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "현재 유저 정보 조회 성공", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = UserDto.class))}),
            @ApiResponse(responseCode = "400", description = "현재 유저 정보 조회 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(
            @Parameter(description = "AccessToken 을 입력해주세요.", required = true) @CurrentUser UserPrincipal userPrincipal
    ) {
        return ResponseEntity.ok(userService.getCurrentUser(userPrincipal));
    }

    @Operation(summary = "유저 탈퇴", description = "현재 유저를 탈퇴 처리 합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "유저 탈퇴 성공"),
            @ApiResponse(responseCode = "400", description = "유저 탈퇴 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @PostMapping("/inactive")
    public ResponseEntity<Void> inactiveCurrentUser(
            @Parameter(description = "AccessToken 을 입력해주세요.", required = true) @CurrentUser UserPrincipal userPrincipal
    ) {
        userService.inactiveCurrentUser(userPrincipal);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "유저 세부정보 입력", description = "닉네임, 생년월일, 거주지등의 정보를 입력받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "유저 정보 입력 성공"),
            @ApiResponse(responseCode = "400", description = "유저 정보 입력 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @PatchMapping
    public ResponseEntity<Void> signUpCurrentUser(
            @Parameter(description = "AccessToken 을 입력해주세요.", required = true) @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "SignUpUserReq 를 확인해주세요.", required = true) @RequestBody SignUpUserReq signUpUserReq
    ) {
        userService.signUpCurrentUser(userPrincipal, signUpUserReq);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "유저 닉네임 입력", description = "닉네임을 입력받습니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "유저 닉네임 입력 성공"),
            @ApiResponse(responseCode = "400", description = "유저 닉네임 입력 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @PatchMapping("/nickname")
    public ResponseEntity<Void> updateUserNickname(
            @Parameter(description = "AccessToken 을 입력해주세요.", required = true) @CurrentUser final UserPrincipal userPrincipal,
            @Parameter(name = "nickname", description = "nickname", required = true) @RequestParam final String nickname
    ) {
        userService.updateUserNickname(userPrincipal, nickname);
        return ResponseEntity.noContent().build();
    }

}
