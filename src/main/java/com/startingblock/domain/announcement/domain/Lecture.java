package com.startingblock.domain.announcement.domain;

import com.startingblock.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "announcement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Lecture extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String liberal;

    private Integer credit;

    private String session;

    private String instructor;

    private String content;

    @Builder
    public Lecture(String title, String liberal, Integer credit, String session, String instructor, String content) {
        this.title = title;
        this.liberal = liberal;
        this.credit = credit;
        this.session = session;
        this.instructor = instructor;
        this.content = content;
    }

}
