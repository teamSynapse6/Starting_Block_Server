package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data
public class RoadmapAnnouncementRes {

    private Long announcementId;
    private String title;
    private String target;
    private String content;
    private Boolean isBookmarked;

    @QueryProjection
    public RoadmapAnnouncementRes(Long announcementId, String title, String target, String content, Boolean isBookmarked) {
        this.announcementId = announcementId;
        this.title = title;
        this.target = target;
        this.content = content;
        this.isBookmarked = isBookmarked;
    }

}
