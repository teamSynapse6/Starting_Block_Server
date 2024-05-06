package com.startingblock.domain.campus.domain;

import com.startingblock.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "campus")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Campus extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title; // 공고 타이틀

    @Column(name = "insert_date")
    private LocalDate insertDate; // 등록일자

    @Lob
    @Column(name = "detail_url")
    private String detailUrl; // 상세 URL

    @Enumerated(EnumType.STRING)
    @Column(name = "university")
    private University university;

    @Enumerated(EnumType.STRING)
    @Column(name = "keyword")
    private Keyword keyword;

    @Builder
    public Campus(final String title, final LocalDate insertDate, final String detailUrl, final University university, final Keyword keyword) {
        this.title = title;
        this.insertDate = insertDate;
        this.detailUrl = detailUrl;
        this.university = university;
        this.keyword = keyword;
    }
}
