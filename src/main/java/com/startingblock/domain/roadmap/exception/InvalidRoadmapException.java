package com.startingblock.domain.roadmap.exception;

public class InvalidRoadmapException extends RuntimeException {

    public InvalidRoadmapException() {
        super("유효하지 않는 로드맵 ID 입니다.");
    }

}
