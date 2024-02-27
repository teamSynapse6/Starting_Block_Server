package com.startingblock.domain.roadmap.exception;

public class RoadmapMismatchUserException extends RuntimeException {

        public RoadmapMismatchUserException() {
            super("로드맵 작성자와 사용자가 일치하지 않습니다.");
        }

}
