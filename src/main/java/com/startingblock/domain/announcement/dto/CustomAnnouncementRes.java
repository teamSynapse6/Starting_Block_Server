package com.startingblock.domain.announcement.dto;

import com.startingblock.domain.announcement.domain.AnnouncementType;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CustomAnnouncementRes {

    private String announcementType; // 교외, 교내

    private String keyword; // 교외: 주관기관, 교내: 프로그램 구분

    private String title; // 공고명

    private String dday; // D-day

    private String detailUrl;

    private Long announcementId;
}
