package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AnnouncementRes {

    private Long announcementId;
    private String departmentName;
    private String title;
    private String startDate;
    private String endDate;
    private Boolean isBookmarked;

    @QueryProjection
    public AnnouncementRes(Long announcementId, String departmentName, String title, String startDate, String endDate, Boolean isBookmarked) {
        this.announcementId = announcementId;
        this.departmentName = departmentName;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.isBookmarked = isBookmarked;
    }

}
