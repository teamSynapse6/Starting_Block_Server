package com.startingblock.domain.announcement.dto;

import com.startingblock.domain.announcement.domain.Announcement;
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
    private String link;
    private String region;
    private String postTarget;
    private Integer saved;
    private String classification;
    private String contact;

    public static AnnouncementDetailRes of(Announcement announcement) {
        return AnnouncementDetailRes.builder()
                .id(announcement.getId())
                .organization(announcement.getOrganizationName())
                .title(announcement.getTitle())
                .content(announcement.getContent())
                .startDate(announcement.getStartDate().toString())
                .endDate(announcement.getEndDate().toString())
                .target(announcement.getPostTarget())
                .targetAge(announcement.getPostTargetAge())
                .supportType(announcement.getSupportType())
                .link(announcement.getDetailUrl())
                .region(announcement.getAreaName())
                .postTarget(announcement.getPostTarget())
                .saved(announcement.getRoadmapCount())
                .classification(announcement.getAnnouncementType().toString())
                .contact(announcement.getContact())
                .build();
    }

}
