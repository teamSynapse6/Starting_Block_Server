package com.startingblock.domain.announcement.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementServiceImplTest {

    @Test
    void refreshAnnouncements() {
        String nowDate = LocalDate.now().toString().replace("-", "");
        System.out.println(nowDate);
    }

}