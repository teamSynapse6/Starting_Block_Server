package com.startingblock.domain.announcement.domain;

import lombok.Getter;

@Getter
public enum Keyword {
    CLUB("동아리"),
    CAMP("캠프"),
    CONTEST("경진대회"),
    LECTURE("특강"),
    MENTORING("멘토링"),
    ETC("기타"),
    SPACE("공간");

    private final String keyword;

    Keyword(String keyword) {
        this.keyword = keyword;
    }

    public static Keyword of(String keyword) {
        for (Keyword value : values()) {
            if (value.getKeyword().equals(keyword)) {
                return value;
            }
        }
        return ETC;
    }

}
