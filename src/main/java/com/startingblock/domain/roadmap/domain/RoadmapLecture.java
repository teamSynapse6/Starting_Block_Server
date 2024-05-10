package com.startingblock.domain.roadmap.domain;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.Lecture;
import com.startingblock.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roadmap_lecture")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RoadmapLecture extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_id", nullable = false)
    private Lecture lecture;

    @Builder
    public RoadmapLecture(Roadmap roadmap, Lecture lecture) {
        this.roadmap = roadmap;
        this.lecture = lecture;
    }

}
