package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data
public class LectureRes {

    private Long lectureId;
    private String title;
    private String liberal;
    private Integer credit;
    private String session;
    private String instructor;
    private String content;
    private Boolean isBookmarked;

    @QueryProjection
    public LectureRes(Long lectureId, String title, String liberal, Integer credit, String session, String instructor, String content, Boolean isBookmarked) {
        this.lectureId = lectureId;
        this.title = title;
        this.liberal = liberal;
        this.credit = credit;
        this.session = session;
        this.instructor = instructor;
        this.content = content;
        this.isBookmarked = isBookmarked;
    }

}
