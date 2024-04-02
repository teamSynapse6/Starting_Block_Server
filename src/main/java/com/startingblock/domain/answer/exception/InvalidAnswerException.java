package com.startingblock.domain.answer.exception;

public class InvalidAnswerException extends RuntimeException {

    public InvalidAnswerException() {
        super("유효하지 않은 답변 ID입니다");
    }
}
