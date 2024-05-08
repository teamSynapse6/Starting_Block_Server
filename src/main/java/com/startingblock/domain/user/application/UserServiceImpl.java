package com.startingblock.domain.user.application;

import com.startingblock.domain.auth.domain.Token;
import com.startingblock.domain.auth.domain.repository.TokenRepository;
import com.startingblock.domain.auth.exception.InvalidTokenException;
import com.startingblock.domain.common.Status;
import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.dto.SignUpUserReq;
import com.startingblock.domain.user.dto.UserDto;
import com.startingblock.domain.user.exception.AlreadyExistNicknameException;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.AuthConfig;
import com.startingblock.global.config.security.token.UserPrincipal;
import com.startingblock.global.infrastructure.feign.KakaoClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final KakaoClient kakaoClient;
    private final AuthConfig authConfig;

    @Override
    @Transactional
    public void inactiveCurrentUser(final UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        kakaoClient.unlinkUser(
                "KakaoAK " + authConfig.getAuth().getKakaoAdminKey(),
                "user_id",
                user.getProviderId()
        );

        Token refreshToken = tokenRepository.findByProviderId(user.getProviderId())
                .orElseThrow(InvalidTokenException::new);

        user.updateStatus(Status.DELETED);
        tokenRepository.delete(refreshToken);
    }

    @Override
    @Transactional
    public void signUpCurrentUser(final UserPrincipal userPrincipal, final SignUpUserReq signUpUserReq) {
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow(InvalidUserException::new);

        Boolean isExistNickname = userRepository.existsByNickname(signUpUserReq.getNickname());
        if(isExistNickname) {
            throw new AlreadyExistNicknameException(signUpUserReq.getNickname());
        }

        user.updateNickname(signUpUserReq.getNickname());
        user.updateBirth(signUpUserReq.getBirth());
        user.updateIsCompletedBusinessRegistration(signUpUserReq.getIsCompletedBusinessRegistration());
        user.updateResidence(signUpUserReq.getResidence());
        user.updateUniversity(signUpUserReq.getUniversity());
    }

    @Override
    public UserDto getCurrentUser(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        return UserDto.toDto(user);
    }

}
