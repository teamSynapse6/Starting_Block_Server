package com.startingblock.domain.announcement.application;

import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.global.infrastructure.feign.OpenDataClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final OpenDataClient openDataClient;

}
