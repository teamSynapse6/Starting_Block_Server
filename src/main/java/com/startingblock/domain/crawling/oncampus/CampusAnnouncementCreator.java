package com.startingblock.domain.crawling.oncampus;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.Keyword;
import com.startingblock.domain.announcement.domain.University;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CampusAnnouncementCreator {

    public static Announcement createCampusAnnouncement(final String title, final LocalDateTime insertDate, final String detailUrl, final University university, final Keyword keyword) {

        return Announcement.builder()
                .postSN(String.valueOf(UUID.randomUUID()))
                .title(title)
                .insertDate(insertDate)
                .detailUrl(detailUrl)
                .announcementType(AnnouncementType.ON_CAMPUS)
                .university(university)
                .keyword(keyword)
                .build();
    }
}
