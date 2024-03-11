package com.startingblock.domain.announcement.exception;

public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException() {
        super("권한이 없습니다.");
    }

}
