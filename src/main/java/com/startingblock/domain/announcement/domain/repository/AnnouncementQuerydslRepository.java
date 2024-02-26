package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.dto.AnnouncementRes;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

public interface AnnouncementQuerydslRepository {

    List<String> findAnnouncementPostIds();
    Slice<AnnouncementRes> findAnnouncements(Long userId, Pageable pageable, String businessAge, String region, String supportType, String sort, String search);

}
