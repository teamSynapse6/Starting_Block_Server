package com.startingblock.domain.roadmap.exception;

public class EmptyRoadmapException extends RuntimeException {

        public EmptyRoadmapException() {
            super("로드맵이 비어있습니다.");
        }

}
