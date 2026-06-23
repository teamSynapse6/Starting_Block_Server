package com.startingblock.domain.auth.presentation;


import jakarta.validation.Valid;

import com.startingblock.domain.auth.dto.*;
import com.startingblock.global.config.security.token.CurrentUser;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.payload.ErrorResponse;
import com.startingblock.domain.auth.application.AuthService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Authorization", description = "Authorization API")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "카카오 로그인", description = "카카오 로그인을 수행합니다.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "카카오 로그인 성공", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = SignInRes.class))}),
            @ApiResponse(responseCode = "400", description = "카카오 로그인 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @PostMapping(value = "/sign-in")
    public ResponseEntity<SignInRes> signIn(
            @Parameter(description = "SignInReq Schema를 확인해주세요.", required = true) @RequestBody SignInReq signInReq
    ) {
        return ResponseEntity.ok(authService.kakaoSignIn(signInReq));
    }

    @Operation(summary = "애플 로그인", description = "애플 로그인을 수행합니다.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "애플 로그인 성공", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = SignInRes.class))}),
            @ApiResponse(responseCode = "400", description = "애플 로그인 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @PostMapping(value = "/sign-in/apple")
    public ResponseEntity<SignInRes> appleSignIn(
            @Parameter(description = "AppleSignInReq Schema를 확인해주세요.", required = true) @RequestBody AppleSignInReq appleSignInReq
    ) {
        return ResponseEntity.ok(authService.appleSignIn(appleSignInReq));
    }

    @Operation(summary = "토큰 갱신", description = "신규 토큰 갱신을 수행합니다.", security = {})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AuthRes.class))}),
            @ApiResponse(responseCode = "400", description = "토큰 갱신 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @PostMapping(value = "/refresh")
    public ResponseEntity<AuthRes> refresh(
            @Parameter(description = "Schemas의 RefreshTokenRequest를 참고해주세요.", required = true) @Valid @RequestBody RefreshTokenReq tokenRefreshRequest
    ) {
        return ResponseEntity.ok(authService.refresh(tokenRefreshRequest));
    }

    @Operation(summary = "유저 로그아웃", description = "유저 로그아웃을 수행합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "400", description = "로그아웃 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @PostMapping(value = "/sign-out")
    public ResponseEntity<Void> signOut(
            @Parameter(description = "Schemas의 RefreshTokenRequest를 참고해주세요.", required = true) @Valid @RequestBody RefreshTokenReq tokenRefreshRequest
    ) {
        authService.signOut(tokenRefreshRequest);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "유저 탈퇴", description = "현재 유저를 provider별로 연결 해제 후 탈퇴 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "유저 탈퇴 성공"),
            @ApiResponse(responseCode = "400", description = "유저 탈퇴 실패", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))}),
    })
    @DeleteMapping(value = "/inactive")
    public ResponseEntity<Void> withdraw(
            @Parameter(description = "AccessToken 을 입력해주세요.", required = true) @CurrentUser UserPrincipal userPrincipal
    ) {
        authService.withdraw(userPrincipal);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "카카오 연결 해제 웹훅", description = "카카오 외부 연결 해제 콜백을 처리합니다.", security = {})
    @RequestMapping(value = "/webhook/kakao/unlink", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> kakaoUnlinkWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "app_id", required = false) String appId,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "referrer_type", required = false) String referrerType
    ) {
        log.info(
                "Kakao unlink webhook received app_id={} user_id={} referrer_type={} auth_present={}",
                appId,
                userId,
                referrerType,
                authorization != null
        );
        authService.handleKakaoUnlinkWebhook(authorization, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "애플 서버 알림 웹훅", description = "Sign in with Apple 서버 알림을 처리합니다.", security = {})
    @PostMapping(value = "/webhook/apple")
    public ResponseEntity<Void> appleServerNotification(
            @RequestBody(required = false) AppleServerNotificationReq request
    ) {
        log.info("Apple server notification received payload_present={}", request != null && request.getPayload() != null);
        authService.handleAppleServerNotification(request);
        return ResponseEntity.ok().build();
    }

}
