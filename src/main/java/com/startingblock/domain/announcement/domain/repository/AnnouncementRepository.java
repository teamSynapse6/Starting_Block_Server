package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AnnouncementRepository extends JpaRepository<Announcement, Long> , AnnouncementQuerydslRepository {
}
