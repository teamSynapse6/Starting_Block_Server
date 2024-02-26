package com.startingblock.domain.announcement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class AnnouncementDetailRes {

    private Long id;
    private String organization;
    private String title;
    private String content;
    private String startDate;
    private String endDate;
    private String target;
    private String targetAge;
    private String supportType;
    private String detailUrl;
    private String region;
    private String postTarget;
    private Integer saveCount;
    private String classification;
    private String contact;

}
