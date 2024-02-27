package com.startingblock.domain.roadmap.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data
public class RoadmapDetailRes {

    private Long roadmapId;
    private String title;
    private Integer sequence;

    @QueryProjection
    public RoadmapDetailRes(Long roadmapId, String title, Integer sequence) {
        this.roadmapId = roadmapId;
        this.title = title;
        this.sequence = sequence;
    }

}
