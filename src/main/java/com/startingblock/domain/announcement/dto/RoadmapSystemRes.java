package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import com.startingblock.domain.announcement.domain.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class RoadmapSystemRes {

    private Long announcementId;
    private String title;
    private String target;
    private String content;
    private Boolean isBookmarked;

    public static List<RoadmapSystemRes> toDto(final List<Announcement> announcements) {
        return announcements.stream()
                .map(announcement -> RoadmapSystemRes.builder()
                        .announcementId(announcement.getId())
                        .target(announcement.getPostTarget())
                        .title(announcement.getTitle())
                        .content(announcement.getContent())
                        .isBookmarked(true)
                        .build())
                .toList();
    }

    @Builder
    @QueryProjection
    public RoadmapSystemRes(Long announcementId, String title, String target, String content, Boolean isBookmarked) {
        this.announcementId = announcementId;
        this.title = title;
        this.target = target;
        this.content = content;
        this.isBookmarked = isBookmarked;
    }

}
