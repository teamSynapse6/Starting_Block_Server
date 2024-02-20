package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.KStartUpAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KStartUpAnnouncementRepository extends JpaRepository<KStartUpAnnouncement, Long>, AnnouncementRepository {
}
