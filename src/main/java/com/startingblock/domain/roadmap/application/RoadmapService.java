package com.startingblock.domain.roadmap.application;

import com.startingblock.domain.announcement.dto.AnnouncementRes;
import com.startingblock.domain.announcement.dto.RecommendAnnouncementRes;
import com.startingblock.domain.announcement.dto.RoadmapLectureRes;
import com.startingblock.domain.roadmap.dto.SavedRoadmapRes;
import com.startingblock.domain.roadmap.dto.RoadmapDetailRes;
import com.startingblock.domain.roadmap.dto.RoadmapRegisterReq;
import com.startingblock.domain.roadmap.dto.SwapRoadmapReq;
import com.startingblock.global.config.security.token.UserPrincipal;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface RoadmapService {

    void registerRoadmaps(UserPrincipal userPrincipal, RoadmapRegisterReq roadMapRegisterReq);
    void addRoadmapAnnouncement(UserPrincipal userPrincipal, Long roadmapId, Long announcementId);
    void deleteRoadmapAnnouncement(UserPrincipal userPrincipal, Long roadmapId, Long announcementId);
    List<RoadmapDetailRes> findRoadmaps(UserPrincipal userPrincipal);
    List<RoadmapDetailRes> deleteRoadmap(UserPrincipal userPrincipal, Long roadmapId);
    List<SavedRoadmapRes> findAnnouncementSavedRoadmap(UserPrincipal userPrincipal, Long announcementId);
    List<RoadmapDetailRes> swapRoadmap(UserPrincipal userPrincipal, SwapRoadmapReq swapRoadmapReq);
    List<RoadmapDetailRes> leapCurrentRoadmap(UserPrincipal userPrincipal);
    List<RoadmapDetailRes> addRoadmap(UserPrincipal userPrincipal, String roadmapTitle);
    List<?> findListOfRoadmap(UserPrincipal userPrincipal, Long roadmapId, String type);
    void addRoadmapLecture(UserPrincipal userPrincipal, Long roadmapId, Long lectureId);
    List<RoadmapLectureRes> findLecturesOfRoadmap(UserPrincipal userPrincipal, Long roadmapId);
    void deleteRoadmapLecture(UserPrincipal userPrincipal, Long roadmapId, Long lectureId);
    List<SavedRoadmapRes> findLectureSavedRoadmap(UserPrincipal userPrincipal, Long lectureId);
    List<RecommendAnnouncementRes> recommendOffCampusAnnouncements(UserPrincipal userPrincipal, Long roadmapId);
    List<RecommendAnnouncementRes> recommendOnCampusAnnouncements(UserPrincipal userPrincipal, Long roadmapId);

}
