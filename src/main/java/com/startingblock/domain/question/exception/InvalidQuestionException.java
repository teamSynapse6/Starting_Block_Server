package com.startingblock.domain.question.exception;

public class InvalidQuestionException extends RuntimeException {

    public InvalidQuestionException() {
        super("올바르지 않은 질문 ID 입니다.");
    }
}
