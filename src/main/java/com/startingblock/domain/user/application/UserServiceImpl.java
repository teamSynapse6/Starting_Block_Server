package com.startingblock.domain.user.application;

import com.startingblock.domain.auth.domain.Token;
import com.startingblock.domain.auth.domain.repository.TokenRepository;
import com.startingblock.domain.auth.exception.InvalidTokenException;
import com.startingblock.domain.common.Status;
import com.startingblock.domain.user.domain.Provider;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.dto.SignUpUserReq;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.AuthConfig;
import com.startingblock.global.config.security.token.UserPrincipal;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final AuthConfig authConfig;

    @Override
    @Transactional
    public void inactiveCurrentUser(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        if (user.getProvider().equals(Provider.KAKAO)) {
            RestClient restClient = RestClient.builder()
                    .baseUrl("https://kapi.kakao.com")
                    .requestFactory(new HttpComponentsClientHttpRequestFactory())
                    .build();

            Map<String, String> body = Map.of("target_id_type", "user_id", "target_id", user.getProviderId());

            restClient.post()
                    .uri("/v1/user/unlink")
                    .header("Authorization", "KakaoAK " + authConfig.getAuth().getKakaoAdminKey())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        }

        Token refreshToken = tokenRepository.findByProviderId(user.getProviderId())
                .orElseThrow(InvalidTokenException::new);

        user.updateStatus(Status.DELETED);
        tokenRepository.delete(refreshToken);
    }

    @Override
    @Transactional
    public void signUpCurrentUser(final UserPrincipal userPrincipal, final SignUpUserReq signUpUserReq) {
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow(InvalidUserException::new);

        user.updateNickname(signUpUserReq.getNickname());
        user.updateBirth(signUpUserReq.getBirth());
        user.updateIsCompletedBusinessRegistration(signUpUserReq.getIsCompletedBusinessRegistration());
        user.updateResidence(signUpUserReq.getResidence());
        user.updateUniversity(signUpUserReq.getUniversity());
    }

}
