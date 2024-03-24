package com.startingblock.domain.roadmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class SwapRoadmapReq {

    @Schema(description = "순서 변경 후 Roadmap Id 순서", example = "[64, 67, 52, 78]")
    private List<Long> roadmapIds;

}
