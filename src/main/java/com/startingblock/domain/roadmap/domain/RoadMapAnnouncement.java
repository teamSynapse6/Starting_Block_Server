package com.startingblock.domain.roadmap.domain;

import com.startingblock.domain.announcement.domain.Announcement;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "road_map_announcement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RoadMapAnnouncement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "road_map_id", nullable = false)
    private RoadMap roadMap;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id", nullable = false)
    private Announcement announcement;

    @Builder
    public RoadMapAnnouncement(RoadMap roadMap, Announcement announcement) {
        this.roadMap = roadMap;
        this.announcement = announcement;
    }

}
