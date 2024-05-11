package com.startingblock.domain.roadmap.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data
public class SavedRoadmapRes {

    private Long roadmapId;
    private String title;
    private Boolean isAnnouncementSaved;

    @QueryProjection
    public SavedRoadmapRes(Long roadmapId, String title, Boolean isAnnouncementSaved) {
        this.roadmapId = roadmapId;
        this.title = title;
        this.isAnnouncementSaved = isAnnouncementSaved;
    }

}
