package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.Keyword;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OnCampusAnnouncementRes {

    private Long announcementId;
    private String keyword;
    private String title;
    private LocalDateTime insertDate;
    private Boolean isBookmarked;

    @Builder
    @QueryProjection
    public OnCampusAnnouncementRes(Long announcementId, String keyword, String title, LocalDateTime insertDate, Boolean isBookmarked) {
        this.announcementId = announcementId;
        this.keyword = keyword;
        this.title = title;
        this.insertDate = insertDate;
        this.isBookmarked = isBookmarked;
    }

}
