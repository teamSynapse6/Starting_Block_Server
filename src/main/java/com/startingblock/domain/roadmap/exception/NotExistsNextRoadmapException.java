package com.startingblock.domain.roadmap.exception;

public class NotExistsNextRoadmapException extends RuntimeException {

    public NotExistsNextRoadmapException() {
        super("다음 진행할 로드맵이 존재하지 않습니다.");
    }

}
