package com.startingblock.domain.announcement.dto;

import com.startingblock.domain.announcement.domain.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class SupportGroupRes {

    private Long announcementId;
    private String title;
    private String content;

    public static List<SupportGroupRes> toDto(final List<Announcement> supportGroups) {
        return supportGroups.stream()
                .map(supportGroup -> SupportGroupRes.builder()
                        .announcementId(supportGroup.getId())
                        .title(supportGroup.getTitle())
                        .content(supportGroup.getContent())
                        .build())
                .toList();
    }

}
