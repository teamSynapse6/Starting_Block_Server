package com.startingblock.domain.reply.exception;

public class InvalidReplyException extends RuntimeException {

    public InvalidReplyException() {
        super("유효하지 않은 답글 ID입니다.");
    }
}
