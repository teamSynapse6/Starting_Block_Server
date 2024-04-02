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
public class RoadmapAnnouncementRes {

    private Long announcementId;
    private String department;
    private String title;
    private String dDay;
    private Boolean isBookmarked;

    public static List<RoadmapAnnouncementRes> of(final List<Announcement> announcements) {
        return announcements.stream()
                .map(announcement -> RoadmapAnnouncementRes.builder()
                        .announcementId(announcement.getId())
                        .department(announcement.getBizPrchDprtNm())
                        .title(announcement.getTitle())
                        .dDay(announcement.getEndDate() == null ? announcement.getNonDate() : String.valueOf(ChronoUnit.DAYS.between(LocalDateTime.now(), announcement.getEndDate())))
                        .isBookmarked(true)
                        .build())
                .sorted(Comparator.comparing(roadmapAnnouncementRes -> {
                    try {
                        return Integer.parseInt(roadmapAnnouncementRes.getDDay());
                    } catch (NumberFormatException e) {
                        return Integer.MAX_VALUE;
                    }
                }))
                .toList();
    }

}
