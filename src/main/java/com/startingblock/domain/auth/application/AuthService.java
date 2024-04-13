package com.startingblock.domain.auth.application;

import java.util.Optional;

import com.startingblock.domain.auth.dto.*;
import com.startingblock.domain.common.Status;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.global.DefaultAssert;

import com.startingblock.domain.user.domain.Provider;
import com.startingblock.domain.user.domain.Role;
import com.startingblock.domain.auth.domain.Token;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.auth.domain.repository.TokenRepository;

import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.error.DefaultAuthenticationException;
import com.startingblock.global.payload.ErrorCode;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;



@RequiredArgsConstructor
@Service
public class AuthService {

    private final CustomTokenProviderService customTokenProviderService;
    private final TokenRepository tokenRepository;
    private final UserRepository userRepository;

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
    public AuthRes refresh(final RefreshTokenReq tokenRefreshRequest) {
        //1차 검증
        boolean checkValid = valid(tokenRefreshRequest.getRefreshToken());
        DefaultAssert.isAuthentication(checkValid);

        Token refreshToken = tokenRepository.findByRefreshToken(tokenRefreshRequest.getRefreshToken())
                .orElseThrow(() -> new DefaultAuthenticationException(ErrorCode.INVALID_AUTHENTICATION));
        Authentication authentication = customTokenProviderService.getAuthenticationByEmail(refreshToken.getProviderId());

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

    private boolean valid(String refreshToken){

        //1. 토큰 형식 물리적 검증
        boolean validateCheck = customTokenProviderService.validateToken(refreshToken);
        DefaultAssert.isTrue(validateCheck, "Token 검증에 실패하였습니다.");

        //2. refresh token 값을 불러온다.
        Optional<Token> token = tokenRepository.findByRefreshToken(refreshToken);
        DefaultAssert.isTrue(token.isPresent(), "탈퇴 처리된 회원입니다.");

        //3. email 값을 통해 인증값을 불러온다
        Authentication authentication = customTokenProviderService.getAuthenticationByEmail(token.get().getProviderId());
        DefaultAssert.isTrue(token.get().getProviderId().equals(authentication.getName()), "사용자 인증에 실패하였습니다.");

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
                && user.getBirth() != null
                && user.getIsCompletedBusinessRegistration() != null
                && user.getResidence() != null
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
