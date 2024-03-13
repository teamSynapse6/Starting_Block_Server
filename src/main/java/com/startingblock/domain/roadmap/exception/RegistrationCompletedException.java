package com.startingblock.domain.roadmap.exception;

public class RegistrationCompletedException extends RuntimeException {

    public RegistrationCompletedException() {
        super("등록이 이미 완료됐습니다.");
    }

}
