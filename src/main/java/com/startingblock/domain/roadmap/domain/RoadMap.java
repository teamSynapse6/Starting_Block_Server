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
@Table(name = "road_map")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class RoadMap extends BaseEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(name = "content")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "sequence")
    private Integer sequence;

    public void updateContent(String content) {
        this.content = content;
    }

    public void updateSequence(Integer sequence) {
        this.sequence = sequence;
    }

    @Builder
    public RoadMap(String content, User user, Integer sequence) {
        this.content = content;
        this.user = user;
        this.sequence = sequence;
    }

}
