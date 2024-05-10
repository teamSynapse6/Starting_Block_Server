package com.startingblock.domain.announcement.dto;

import com.startingblock.domain.announcement.domain.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class SystemRes {

    private String title;
    private String target;
    private String content;

    public static List<SystemRes> toDto(final List<Announcement> systems) {
        return systems.stream()
                .map(system -> SystemRes.builder()
                        .title(system.getTitle())
                        .target(system.getPostTarget())
                        .content(system.getContent())
                        .build())
                .toList();
    }

}
