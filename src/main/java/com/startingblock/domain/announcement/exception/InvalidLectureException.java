package com.startingblock.domain.announcement.exception;

public class InvalidLectureException extends RuntimeException {

        public InvalidLectureException() {
            super("유효하지 않는 강의 ID 입니다.");
        }

}
