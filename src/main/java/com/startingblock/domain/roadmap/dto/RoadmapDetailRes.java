package com.startingblock.domain.roadmap.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.startingblock.domain.roadmap.domain.Roadmap;
import com.startingblock.domain.roadmap.domain.RoadmapStatus;
import lombok.Builder;
import lombok.Data;

import java.util.Comparator;
import java.util.List;

@Data
public class RoadmapDetailRes {

    private Long roadmapId;
    private String title;
    private RoadmapStatus roadmapStatus;
    private Integer sequence;

    @Builder
    @QueryProjection
    public RoadmapDetailRes(Long roadmapId, String title, RoadmapStatus roadmapStatus, Integer sequence) {
        this.roadmapId = roadmapId;
        this.title = title;
        this.roadmapStatus = roadmapStatus;
        this.sequence = sequence;
    }

    public static List<RoadmapDetailRes> toRoadmapDetailResList(final List<Roadmap> roadmaps) {
        return roadmaps.stream()
                .map(roadmap -> RoadmapDetailRes.builder()
                        .roadmapId(roadmap.getId())
                        .title(roadmap.getTitle())
                        .roadmapStatus(roadmap.getRoadmapStatus())
                        .sequence(roadmap.getSequence())
                        .build())
                .toList();
    }

}
