package com.startingblock.domain.user.exception;

public class AlreadyExistNicknameException extends RuntimeException {

        public AlreadyExistNicknameException(String nickname) {
            super("Already exist nickname: " + nickname);
        }

}
