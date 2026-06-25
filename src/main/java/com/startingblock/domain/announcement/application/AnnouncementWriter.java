package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.mail.domain.repository.ContactBlocklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AnnouncementWriter {

    private final AnnouncementRepository announcementRepository;
    private final ContactBlocklistRepository contactBlocklistRepository;

    @Transactional
    public void save(List<Announcement> announcements) {
        if (announcements == null || announcements.isEmpty()) {
            return;
        }
        announcements.forEach(this::applyContactBlock);
        announcementRepository.saveAll(announcements);
    }

    private void applyContactBlock(final Announcement announcement) {
        String contact = announcement.getContact();
        if (contact == null || contact.isBlank()) {
            return;
        }
        if (contactBlocklistRepository.existsByContactIgnoreCase(contact.trim())) {
            announcement.blockContact();
        }
    }
}
