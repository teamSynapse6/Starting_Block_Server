package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface AnnouncementRepository extends JpaRepository<Announcement, Long> , AnnouncementQuerydslRepository {

    List<Announcement> findAnnouncementsByAnnouncementTypeAndIsFileUploaded(AnnouncementType announcementType, boolean isFileUsed);

    List<Announcement> findByAnnouncementType(AnnouncementType announcementType);
    List<Announcement> findByAnnouncementTypeAndUniversity(AnnouncementType announcementType, University university);

}
