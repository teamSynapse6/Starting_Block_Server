package com.startingblock.domain.announcement.exception;

public class InvalidAnnouncementException extends RuntimeException {

    public InvalidAnnouncementException() {
        super("유효하지 않는 공고 ID 입니다.");
    }

}
