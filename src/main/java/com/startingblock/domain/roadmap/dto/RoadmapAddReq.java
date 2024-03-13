package com.startingblock.domain.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class RoadmapAddReq {

    @Schema(type = "String", description = "로드맵 제목")
    private String title;

}
