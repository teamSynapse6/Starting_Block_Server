package com.startingblock.domain.roadmap.domain;

import com.startingblock.domain.common.BaseEntity;
import com.startingblock.domain.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "roadmap")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Roadmap extends BaseEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "sequence")
    private Integer sequence;

    @Column(name = "roadmap_status", nullable = false)
    private RoadmapStatus roadmapStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public void updateContent(String content) {
        this.title = content;
    }

    public void updateSequence(Integer sequence) {
        this.sequence = sequence;
    }

    public void updateRoadmapStatus(RoadmapStatus roadmapStatus) {
        this.roadmapStatus = roadmapStatus;
    }

    @Builder
    public Roadmap(String title, Integer sequence, RoadmapStatus roadmapStatus, User user) {
        this.title = title;
        this.sequence = sequence;
        this.roadmapStatus = roadmapStatus;
        this.user = user;
    }

}
