package com.startingblock.domain.roadmap.dto;

import com.startingblock.domain.announcement.domain.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class RoadmapOnCampusRes {

    private Long announcementId;
    private String keyword;
    private String title;
    private LocalDate insertDate;
    private Boolean isBookmarked;

    public static List<RoadmapOnCampusRes> toDto(final List<Announcement> announcements) {
        return announcements.stream()
                .map(announcement -> RoadmapOnCampusRes.builder()
                        .announcementId(announcement.getId())
                        .keyword(announcement.getKeyword().getKeyword())
                        .title(announcement.getTitle())
                        .insertDate(announcement.getInsertDate().toLocalDate())
                        .isBookmarked(true)
                        .build())
                .toList();
    }

}
