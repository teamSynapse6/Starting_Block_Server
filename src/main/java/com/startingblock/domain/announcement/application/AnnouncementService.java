package com.startingblock.domain.announcement.application;

import org.springframework.stereotype.Service;

@Service
public interface AnnouncementService {

    Void refreshAnnouncements();

}
