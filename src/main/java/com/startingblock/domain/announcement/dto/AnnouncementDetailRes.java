package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.startingblock.domain.announcement.domain.Announcement;
import lombok.Builder;
import lombok.Data;

@Data
public class AnnouncementDetailRes {

    private Long id;
    private Boolean isBookmarked;
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
    private Integer saved;
    private String classification;
    private String contact;
    private Boolean isContactExist;
    private Boolean isFileUploaded;

    @Builder
    @QueryProjection
    public AnnouncementDetailRes(Long id, Boolean isBookmarked, String organization, String title, String content, String startDate, String endDate, String target, String targetAge, String supportType, String link, String region, Integer saved, String classification, String contact, Boolean isContactExist, Boolean isFileUploaded) {
        this.id = id;
        this.isBookmarked = isBookmarked;
        this.organization = organization;
        this.title = title;
        this.content = content;
        this.startDate = startDate;
        this.endDate = endDate;
        this.target = target;
        this.targetAge = targetAge;
        this.supportType = supportType;
        this.link = link;
        this.region = region;
        this.saved = saved;
        this.classification = classification;
        this.contact = contact;
        this.isContactExist = isContactExist;
        this.isFileUploaded = isFileUploaded;
    }

}
