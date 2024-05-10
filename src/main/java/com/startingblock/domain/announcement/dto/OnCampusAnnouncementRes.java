package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.startingblock.domain.announcement.domain.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class OnCampusAnnouncementRes {

    private Long announcementId;
    private String title;
    private String content;
    private Boolean isBookmarked;

    @Builder
    @QueryProjection
    public OnCampusAnnouncementRes(Long announcementId, String title, String content, Boolean isBookmarked) {
        this.announcementId = announcementId;
        this.title = title;
        this.content = content;
        this.isBookmarked = isBookmarked;
    }

}
