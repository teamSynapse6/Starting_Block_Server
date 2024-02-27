package com.startingblock.domain.roadmap.exception;

public class InvalidAnnouncementRoadmapException extends RuntimeException {

    public InvalidAnnouncementRoadmapException() {
        super("로드맵에 해당 공고가 존재하지 않습니다.");
    }

}
