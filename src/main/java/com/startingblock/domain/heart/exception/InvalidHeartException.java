package com.startingblock.domain.heart.exception;

public class InvalidHeartException extends RuntimeException {

    public InvalidHeartException() {
        super("유효하지 않은 하트 ID입니다.");
    }
}
