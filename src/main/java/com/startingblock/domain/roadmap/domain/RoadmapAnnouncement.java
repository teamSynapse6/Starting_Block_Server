package com.startingblock.domain.roadmap.domain;

import com.startingblock.domain.announcement.domain.Announcement;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roadmap_announcement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RoadmapAnnouncement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private Roadmap roadmap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @Builder
    public RoadmapAnnouncement(Roadmap roadmap, Announcement announcement) {
        this.roadmap = roadmap;
        this.announcement = announcement;
    }

}
