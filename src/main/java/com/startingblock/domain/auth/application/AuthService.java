package com.startingblock.domain.auth.application;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.startingblock.domain.auth.dto.*;
import com.startingblock.domain.common.Status;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.global.DefaultAssert;

import com.startingblock.domain.user.domain.Provider;
import com.startingblock.domain.user.domain.Role;
import com.startingblock.domain.auth.domain.Token;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.auth.domain.repository.TokenRepository;

import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.config.security.AuthConfig;
import com.startingblock.global.error.DefaultAuthenticationException;
import com.startingblock.global.infrastructure.feign.KakaoClient;
import com.startingblock.global.payload.ErrorCode;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



@Slf4j
@RequiredArgsConstructor
@Service
public class AuthService {

    private final CustomTokenProviderService customTokenProviderService;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final AppleAuthService appleAuthService;
    private final KakaoClient kakaoClient;
    private final AuthConfig authConfig;

    @Transactional
    public SignInRes kakaoSignIn(final SignInReq signInReq) {
        Optional<User> optionalUser = userRepository.findByProviderIdAndStatus(signInReq.getProviderId(), Status.ACTIVE);

        if (optionalUser.isEmpty()) {
            User newUser = User.builder()
                    .provider(Provider.KAKAO)
                    .providerId(signInReq.getProviderId())
                    .email(signInReq.getEmail())
                    .role(Role.USER)
                    .build();

            userRepository.save(newUser);
            optionalUser = Optional.of(newUser);
        }

        User user = optionalUser.get();
        return getUserSignInRes(user);
    }

    @Transactional
    public SignInRes appleSignIn(final AppleSignInReq appleSignInReq) {
        AppleAuthService.AppleAccount appleAccount = appleAuthService.resolve(appleSignInReq);
        Optional<User> optionalUser = userRepository.findByProviderIdAndStatus(appleAccount.providerId(), Status.ACTIVE);

        if (optionalUser.isEmpty()) {
            User newUser = User.builder()
                    .provider(Provider.APPLE)
                    .providerId(appleAccount.providerId())
                    .email(appleAccount.email())
                    .role(Role.USER)
                    .build();

            userRepository.save(newUser);
            optionalUser = Optional.of(newUser);
        } else if (optionalUser.get().getEmail() == null && appleAccount.email() != null) {
            optionalUser.get().updateEmail(appleAccount.email());
        }

        if (appleAccount.refreshToken() != null) {
            optionalUser.get().updateProviderRefreshToken(appleAccount.refreshToken());
        }

        User user = optionalUser.get();
        return getUserSignInRes(user);
    }

    @Transactional
    public AuthRes refresh(final RefreshTokenReq tokenRefreshRequest) {
        //1차 검증
        boolean checkValid = valid(tokenRefreshRequest.getRefreshToken());
        DefaultAssert.isAuthentication(checkValid);

        Token refreshToken = tokenRepository.findByRefreshToken(tokenRefreshRequest.getRefreshToken())
                .orElseThrow(() -> new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION));

        Authentication authentication = customTokenProviderService.getAuthenticationByProviderId(refreshToken.getProviderId());

        //4. refresh token 정보 값을 업데이트 한다.
        //시간 유효성 확인
        TokenMapping tokenMapping;

        Long expirationTime = customTokenProviderService.getExpiration(tokenRefreshRequest.getRefreshToken());
        if (expirationTime > 0) {
            tokenMapping = customTokenProviderService.refreshToken(authentication, refreshToken.getRefreshToken());
        } else {
            tokenMapping = customTokenProviderService.createToken(authentication);
        }

        Token updateRefreshToken = refreshToken.updateRefreshToken(tokenMapping.getRefreshToken());
        tokenRepository.save(updateRefreshToken);

        return AuthRes.builder().
                accessToken(tokenMapping.getAccessToken())
                .refreshToken(updateRefreshToken.getRefreshToken())
                .build();
    }

    @Transactional
    public void signOut(final RefreshTokenReq tokenRefreshRequest) {
        Token refreshToken = tokenRepository.findByRefreshToken(tokenRefreshRequest.getRefreshToken())
                .orElseThrow(() -> new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION));
        tokenRepository.delete(refreshToken);
    }

    @Transactional
    public void withdraw(final UserPrincipal userPrincipal) {
        DefaultAssert.isTrue(userPrincipal != null, "사용자 인증에 실패하였습니다.");
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);
        DefaultAssert.isTrue(user.getStatus() == Status.ACTIVE, "탈퇴 처리된 회원입니다.");

        unlinkProvider(user);

        String providerId = user.getProviderId();
        tokenRepository.deleteByProviderId(providerId);
        user.withdraw();
    }

    @Transactional
    public void handleKakaoUnlinkWebhook(final String authorization, final String userId) {
        String expectedAuthorization = "KakaoAK " + authConfig.getAuth().getKakaoAdminKey();
        if (!StringUtils.hasText(userId)) {
            log.warn("Kakao unlink webhook ignored because user_id is empty");
            return;
        }
        if (!expectedAuthorization.equals(authorization)) {
            log.warn("Kakao unlink webhook ignored because admin key authorization is invalid user_id={}", userId);
            return;
        }

        withdrawActiveUsersByProvider(Provider.KAKAO, userId);
    }

    @Transactional
    public void handleAppleServerNotification(final AppleServerNotificationReq request) {
        if (request == null || !StringUtils.hasText(request.getPayload())) {
            log.warn("Apple server notification ignored because payload is empty");
            return;
        }

        List<AppleAuthService.AppleServerNotificationEvent> events;
        try {
            events = appleAuthService.resolveServerNotification(request.getPayload());
        } catch (Exception exception) {
            log.warn("Apple server notification ignored because payload verification failed");
            return;
        }

        for (AppleAuthService.AppleServerNotificationEvent event : events) {
            if (isAppleWithdrawalEvent(event.type())) {
                withdrawActiveUsersByProvider(Provider.APPLE, event.providerId());
            }
        }
    }

    private void unlinkProvider(final User user) {
        if (user.getProvider() == Provider.KAKAO) {
            kakaoClient.unlinkUser(
                    "KakaoAK " + authConfig.getAuth().getKakaoAdminKey(),
                    "user_id",
                    user.getProviderId()
            );
            return;
        }

        if (user.getProvider() == Provider.APPLE) {
            DefaultAssert.isTrue(user.getProviderRefreshToken() != null, "애플 탈퇴 토큰이 없습니다.");
            appleAuthService.revokeRefreshToken(user.getProviderRefreshToken());
        }
    }

    private void withdrawActiveUsersByProvider(final Provider provider, final String providerId) {
        if (!StringUtils.hasText(providerId)) {
            return;
        }

        List<User> users = userRepository.findAllByProviderAndProviderIdAndStatus(
                provider,
                providerId,
                Status.ACTIVE
        );
        if (users.isEmpty()) {
            log.info("External provider unlink found no active user provider={} provider_id={}", provider, providerId);
            return;
        }

        users.forEach(User::withdraw);
        tokenRepository.deleteByProviderId(providerId);
        log.info("External provider unlink withdrew users provider={} provider_id={} count={}", provider, providerId, users.size());
    }

    private boolean isAppleWithdrawalEvent(final String type) {
        if (!StringUtils.hasText(type)) {
            return false;
        }

        String normalizedType = type.toLowerCase(Locale.ROOT);
        return "consent-revoked".equals(normalizedType)
                || "account-delete".equals(normalizedType)
                || "account-deleted".equals(normalizedType);
    }

    private boolean valid(String refreshToken){

        //1. 토큰 형식 물리적 검증
        boolean validateCheck = customTokenProviderService.validateToken(refreshToken);
        DefaultAssert.isTrue(validateCheck, "Token 검증에 실패하였습니다.");

        //2. refresh token 값을 불러온다.
        Optional<Token> token = tokenRepository.findByRefreshToken(refreshToken);
        DefaultAssert.isTrue(token.isPresent(), "탈퇴 처리된 회원입니다.");

        //3. email 값을 통해 인증값을 불러온다
        Authentication authentication = customTokenProviderService.getAuthenticationByProviderId(token.get().getProviderId());
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        DefaultAssert.isTrue(token.get().getProviderId().equals(userPrincipal.getPassword()), "사용자 인증에 실패하였습니다.");

        return true;
    }

    private SignInRes getUserSignInRes(User user) {
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal,
                null,
                userPrincipal.getAuthorities()
        );

        TokenMapping tokenMapping = customTokenProviderService.createToken(authentication);
        Token refreshToken = Token.builder()
                .refreshToken(tokenMapping.getRefreshToken())
                .providerId(user.getProviderId())
                .build();

        tokenRepository.save(refreshToken);

        boolean isSignUpComplete = user.getNickname() != null
                && user.getEmail() != null
                && user.getIsCompletedBusinessRegistration() != null
                && user.getUniversity() != null;

        AuthRes userAuthRes = AuthRes.builder()
                .accessToken(tokenMapping.getAccessToken())
                .refreshToken(refreshToken.getRefreshToken())
                .build();

        return SignInRes.builder()
                .isSignUpComplete(isSignUpComplete)
                .accessToken(userAuthRes.getAccessToken())
                .refreshToken(userAuthRes.getRefreshToken())
                .tokenType(userAuthRes.getTokenType())
                .build();
    }

}
