package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Builder;
import lombok.Data;

@Data
public class RecommendLectureRes {

    private Long lectureId;
    private Boolean isBookmarked;
    private String title;
    private String liberal;
    private Integer credit;
    private String session;
    private String instructor;
    private String content;

    @Builder
    @QueryProjection
    public RecommendLectureRes(Long lectureId, Boolean isBookmarked, String title, String liberal, Integer credit, String session, String instructor, String content) {
        this.lectureId = lectureId;
        this.isBookmarked = isBookmarked;
        this.title = title;
        this.liberal = liberal;
        this.credit = credit;
        this.session = session;
        this.instructor = instructor;
        this.content = content;
    }

}
