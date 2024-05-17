package com.startingblock.domain.user.application;

import com.startingblock.domain.user.dto.SignUpUserReq;
import com.startingblock.domain.user.dto.UserDto;
import com.startingblock.global.config.security.token.UserPrincipal;
import org.springframework.stereotype.Service;

@Service
public interface UserService {

    void inactiveCurrentUser(UserPrincipal userPrincipal);
    void signUpCurrentUser(UserPrincipal userPrincipal, SignUpUserReq signUpUserReq);
    UserDto getCurrentUser(UserPrincipal userPrincipal);
    void updateUserNickname(UserPrincipal userPrincipal, String nickname);

}
