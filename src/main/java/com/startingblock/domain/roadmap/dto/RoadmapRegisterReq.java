package com.startingblock.domain.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class RoadmapRegisterReq {

    @Schema(type = "List", description = "로드맵 등록 정보")
    private List<RoadmapReq> roadmaps;

    @Data
    public static class RoadmapReq {
        @Schema(type = "String", description = "로드맵 제목")
        private String title;
        @Schema(type = "Int", description = "로드맵 순서(0부터 시작)")
        private Integer sequence;
    }

}
