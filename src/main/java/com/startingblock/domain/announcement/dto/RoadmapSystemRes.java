package com.startingblock.domain.announcement.dto;

import com.startingblock.domain.announcement.domain.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
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

}
