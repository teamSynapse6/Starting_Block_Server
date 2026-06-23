package com.startingblock.domain.user.application;

import com.startingblock.domain.user.domain.User;
import com.startingblock.domain.user.domain.repository.UserRepository;
import com.startingblock.domain.user.dto.SignUpUserReq;
import com.startingblock.domain.user.dto.UserDto;
import com.startingblock.domain.user.exception.AlreadyExistNicknameException;
import com.startingblock.domain.user.exception.InvalidUserException;
import com.startingblock.global.config.security.token.UserPrincipal;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void signUpCurrentUser(final UserPrincipal userPrincipal, final SignUpUserReq signUpUserReq) {
        User user = userRepository.findById(userPrincipal.getId()).orElseThrow(InvalidUserException::new);

        user.updateBirth(signUpUserReq.getBirth());
        user.updateIsCompletedBusinessRegistration(signUpUserReq.getIsCompletedBusinessRegistration());
        user.updateResidence(signUpUserReq.getResidence());
        user.updateUniversity(signUpUserReq.getUniversity());
        user.updateProfileNumber(signUpUserReq.getProfileNumber());
    }

    @Override
    public UserDto getCurrentUser(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        return UserDto.toDto(user);
    }

    @Override
    @Transactional
    public void updateUserNickname(final UserPrincipal userPrincipal, final String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new AlreadyExistNicknameException(nickname);
        }

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(InvalidUserException::new);

        user.updateNickname(nickname);
    }

}
