package com.startingblock.domain.announcement.dto;

import com.startingblock.domain.announcement.domain.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class RecommendAnnouncementRes {

    private Long announcementId;
    private String department;
    private String title;

    public static List<RecommendAnnouncementRes> toDto(final List<Announcement> announcements) {
        return announcements.stream()
                .map(announcement -> RecommendAnnouncementRes.builder()
                        .announcementId(announcement.getId())
                        .department(announcement.getBizPrchDprtNm())
                        .title(announcement.getTitle())
                        .build())
                .toList();
    }

}
