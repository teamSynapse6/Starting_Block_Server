package com.startingblock.domain.crawling.offcampus;

import com.startingblock.domain.announcement.domain.Announcement;

import java.util.List;

public interface OffCampusCrawling {
    void offCampusEmailCrawling(List<Announcement> list);
}
