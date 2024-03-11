package com.startingblock.domain.roadmap.exception;

public class AlreadyExistsRoadmapException extends RuntimeException {

    public AlreadyExistsRoadmapException() {
        super("이미 해당 로드맵에 해당 공고가 등록되어 있습니다.");
    }

}
