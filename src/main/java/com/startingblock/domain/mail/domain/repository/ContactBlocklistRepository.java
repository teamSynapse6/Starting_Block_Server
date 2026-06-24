package com.startingblock.domain.mail.domain.repository;

import com.startingblock.domain.mail.domain.ContactBlocklist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactBlocklistRepository extends JpaRepository<ContactBlocklist, Long> {

    boolean existsByAnnouncementIdAndContactIgnoreCase(Long announcementId, String contact);

    boolean existsByContactIgnoreCase(String contact);
}
