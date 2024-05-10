package com.startingblock.domain.announcement.dto;

import com.startingblock.domain.announcement.domain.Lecture;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class RoadmapLectureRes {

    private String title;
    private String liberal;
    private Integer credit;
    private String session;
    private String instructor;
    private String content;
    private Boolean isBookmarked;

    public static List<RoadmapLectureRes> toDto(final List<Lecture> lectures) {
        return lectures.stream()
                .map(lecture -> RoadmapLectureRes.builder()
                        .title(lecture.getTitle())
                        .liberal(lecture.getLiberal())
                        .credit(lecture.getCredit())
                        .session(lecture.getSession())
                        .instructor(lecture.getInstructor())
                        .content(lecture.getContent())
                        .isBookmarked(true)
                        .build())
                .toList();
    }

}
