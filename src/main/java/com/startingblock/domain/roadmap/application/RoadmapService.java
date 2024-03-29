package com.startingblock.domain.roadmap.application;

import com.startingblock.domain.roadmap.dto.AnnouncementSavedRoadmapRes;
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
    List<AnnouncementSavedRoadmapRes> findAnnouncementSavedRoadmap(UserPrincipal userPrincipal, Long announcementId);
    List<RoadmapDetailRes> swapRoadmap(UserPrincipal userPrincipal, SwapRoadmapReq swapRoadmapReq);
    List<RoadmapDetailRes> leapCurrentRoadmap(UserPrincipal userPrincipal);
    List<RoadmapDetailRes> addRoadmap(UserPrincipal userPrincipal, String roadmapTitle);

}
