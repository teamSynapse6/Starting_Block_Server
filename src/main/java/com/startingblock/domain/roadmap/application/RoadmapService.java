package com.startingblock.domain.roadmap.application;

import com.startingblock.domain.roadmap.dto.RoadmapRegisterReq;
import com.startingblock.global.config.security.token.UserPrincipal;
import org.springframework.stereotype.Service;

@Service
public interface RoadmapService {

    void registerRoadmaps(UserPrincipal userPrincipal, RoadmapRegisterReq roadMapRegisterReq);
    void addRoadmapAnnouncement(UserPrincipal userPrincipal, Long roadmapId, Long announcementId);
    void deleteRoadmapAnnouncement(UserPrincipal userPrincipal, Long roadmapId, Long announcementId);

}
