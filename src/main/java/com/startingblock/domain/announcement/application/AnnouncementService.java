package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.dto.AnnouncementDetailRes;
import com.startingblock.domain.announcement.dto.AnnouncementRes;
import com.startingblock.global.config.security.token.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Service
public interface AnnouncementService {

    void refreshAnnouncements(UserPrincipal userPrincipal);
    Slice<AnnouncementRes> findAnnouncements(UserPrincipal userPrincipal, Pageable pageable, String businessAge, String region, String supportType, String sort, String search);
    AnnouncementDetailRes findAnnouncementDetailById(Long announcementId);

}