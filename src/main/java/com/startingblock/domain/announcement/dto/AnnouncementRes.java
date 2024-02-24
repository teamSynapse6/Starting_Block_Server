package com.startingblock.domain.announcement.dto;

import lombok.Data;

@Data
public class AnnouncementRes {

    private Long announcementId;
    private String departmentName;
    private String title;
    private String startDate;
    private String endDate;
    private Boolean isBookmarked;

}
