package com.startingblock.domain.announcement.domain.repository;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.AnnouncementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Announcement a
            set a.contactBlock = true
            where a.contact is not null
              and lower(trim(a.contact)) = :normalizedContact
            """)
    int blockContactsByNormalizedContact(@Param("normalizedContact") String normalizedContact);
}
