package com.startingblock.domain.notification.presentation;

import com.startingblock.domain.notification.application.NotificationService;
import com.startingblock.domain.notification.dto.FcmTokenDeleteReq;
import com.startingblock.domain.notification.dto.FcmTokenRegisterReq;
import com.startingblock.domain.notification.dto.FcmTokenRegisterRes;
import com.startingblock.domain.notification.dto.NotificationSendReq;
import com.startingblock.domain.notification.dto.NotificationSendRes;
import com.startingblock.global.config.security.token.CurrentUser;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.error.DefaultAuthenticationException;
import com.startingblock.global.payload.ApiResponse;
import com.startingblock.global.payload.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "FCM 토큰 등록 및 푸시 알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/notification", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "FCM 토큰 등록 및 갱신")
    @PostMapping("/token")
    public ResponseEntity<ApiResponse> registerToken(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Valid @RequestBody final FcmTokenRegisterReq request
    ) {
        UserPrincipal currentUser = requireUser(userPrincipal);
        notificationService.registerToken(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.ok(new FcmTokenRegisterRes("FCM 토큰이 등록되었습니다.")));
    }

    @Operation(summary = "FCM 토큰 비활성화")
    @DeleteMapping("/token")
    public ResponseEntity<ApiResponse> deleteToken(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Valid @RequestBody final FcmTokenDeleteReq request
    ) {
        UserPrincipal currentUser = requireUser(userPrincipal);
        notificationService.deleteToken(currentUser.getId(), request.token());
        return ResponseEntity.ok(ApiResponse.ok(new FcmTokenRegisterRes("FCM 토큰이 삭제되었습니다.")));
    }

    @Operation(summary = "사용자에게 FCM 메시지 발송")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse> send(
            @Parameter(name = "Authorization Token") @CurrentUser final UserPrincipal userPrincipal,
            @Valid @RequestBody final NotificationSendReq request
    ) {
        NotificationSendRes result = notificationService.sendManual(userPrincipal, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private UserPrincipal requireUser(final UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION);
        }
        return userPrincipal;
    }
}
