package com.startingblock.domain.heart.exception;

public class NotExistsHeartException extends RuntimeException {

    public NotExistsHeartException() {
        super("해당하는 하트가 존재하지 않습니다.");
    }
}
