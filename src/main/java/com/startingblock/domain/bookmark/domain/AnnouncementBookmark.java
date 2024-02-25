package com.startingblock.domain.bookmark.domain;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.common.BaseEntity;
import com.startingblock.domain.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "announcement_bookmark")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class AnnouncementBookmark extends BaseEntity {

    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_id")
    private Announcement announcement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder
    public AnnouncementBookmark(Announcement announcement, User user) {
        this.announcement = announcement;
        this.user = user;
    }

}
