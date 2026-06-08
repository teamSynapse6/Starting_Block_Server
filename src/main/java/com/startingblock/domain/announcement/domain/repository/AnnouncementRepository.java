package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;


public interface AnnouncementRepository extends JpaRepository<Announcement, Long> , AnnouncementQuerydslRepository {

    List<Announcement> findAnnouncementsByAnnouncementTypeAndIsFileUploaded(AnnouncementType announcementType, boolean isFileUsed);

    @Query("""
            select a from Announcement a
            where a.fileUrl is not null
              and a.fileUrl <> ''
              and (a.isFileUploaded = false or a.isFileUploaded is null)
            """)
    List<Announcement> findFileUploadTargets();

    List<Announcement> findByAnnouncementType(AnnouncementType announcementType);

    List<Announcement> findByAnnouncementTypeAndContactIsNull(AnnouncementType announcementType);

    Boolean existsByDetailUrl(String detailUrl);
}
