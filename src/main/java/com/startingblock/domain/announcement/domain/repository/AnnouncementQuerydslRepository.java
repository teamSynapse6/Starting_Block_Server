package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.common.Status;

import java.util.List;

public interface AnnouncementQuerydslRepository {

    List<String> findAnnouncementPostIds();

}
