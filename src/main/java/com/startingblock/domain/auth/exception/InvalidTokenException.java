package com.startingblock.domain.auth.exception;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("Invalid Token");
    }

}
