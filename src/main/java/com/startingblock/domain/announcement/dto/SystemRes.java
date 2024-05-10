package com.startingblock.domain.announcement.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;


@Data
public class SystemRes {

    private Long systemId;
    private String title;
    private String target;
    private String content;
    private Boolean isBookmarked;

    @QueryProjection
    public SystemRes(Long systemId, String title, String target, String content, Boolean isBookmarked) {
        this.systemId = systemId;
        this.title = title;
        this.target = target;
        this.content = content;
        this.isBookmarked = isBookmarked;
    }

}
