package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.Keyword;
import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.announcement.dto.*;
import com.startingblock.domain.user.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface AnnouncementQuerydslRepository {

    List<String> findAnnouncementPostIds();
    Slice<AnnouncementRes> findAnnouncements(Long userId, Pageable pageable, String businessAge, String region, String supportType, String sort, String search);
    AnnouncementDetailRes findAnnouncementDetail(Long userId, Long announcementId);
    List<AnnouncementRes> findThreeRandomAnnouncement(Long userId);
    List<Announcement> findListOfRoadmapByRoadmapId(Long userId, Long roadmapId, String type);
    List<Announcement> findCustomAnnouncementOnOff(User user, University university);
    List<Announcement> findCustomAnnouncementOff(User user);
    List<OnCampusAnnouncementRes> findOnCampusAnnouncements(Long userId, University university, Keyword keyword);
    List<SystemRes> findSystems(Long userId, University university);
    List<LectureRes> findLectures(Long id, University university);

}
