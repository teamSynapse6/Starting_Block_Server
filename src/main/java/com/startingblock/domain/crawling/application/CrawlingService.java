package com.startingblock.domain.crawling.application;

import com.startingblock.domain.announcement.domain.AnnouncementType;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.crawling.offcampus.OpenData;
import com.startingblock.domain.crawling.oncampus.CampusCrawling;
import com.startingblock.domain.crawling.oncampus.EwhaUniv;
import com.startingblock.domain.crawling.oncampus.HUFSUniv;
import com.startingblock.domain.crawling.oncampus.HanYangUniv;
import com.startingblock.domain.crawling.oncampus.KonKukUniv;
import com.startingblock.domain.crawling.oncampus.KoreaUniv;
import com.startingblock.domain.crawling.oncampus.KyungHeeUniv;
import com.startingblock.domain.crawling.oncampus.SeoulTechUniv;
import com.startingblock.domain.crawling.oncampus.SeoulUniv;
import com.startingblock.domain.crawling.oncampus.SungKyunKwanUniv;
import com.startingblock.domain.crawling.oncampus.YonSeiUniv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlingService {

    private final AnnouncementRepository announcementRepository;

    // TODO: 교내 초기 크롤링
    @Transactional
    public void onCampusInitialCrawling() {
        onCampusCrawling();
    }

    // TODO: 교내 공고 크롤링 자동화
    @Transactional
    @Scheduled(cron = "0 15 3 * * ?") // 매일 새벽 3시 15분에 실행
    public void onCampusAutoCrawling() {
        log.info("교내 공고 크롤링 자동화 시작");
        onCampusCrawling();
    }

    private void onCampusCrawling() {
        ArrayList<CampusCrawling> campusList = new ArrayList<>();
        campusList.add(new KonKukUniv(announcementRepository));
        campusList.add(new SeoulTechUniv(announcementRepository));
        campusList.add(new SeoulUniv(announcementRepository));
        campusList.add(new SungKyunKwanUniv(announcementRepository));
        campusList.add(new YonSeiUniv(announcementRepository));
        campusList.add(new HanYangUniv(announcementRepository));
        campusList.add(new EwhaUniv(announcementRepository));
        campusList.add(new KyungHeeUniv(announcementRepository));
        campusList.add(new KoreaUniv(announcementRepository));
        campusList.add(new HUFSUniv(announcementRepository));

        for (CampusCrawling campusCrawling : campusList) {
            announcementRepository.saveAll(campusCrawling.onCampusCrawling());
        }
    }

    // TODO: k-startup 공고 이메일 크롤링
    @Transactional
    public void offCampusEmailCrawling() {
        OpenData openData = new OpenData();
        openData.offCampusEmailCrawling(announcementRepository.findByAnnouncementType(AnnouncementType.OPEN_DATA));
    }

    // TODO: k-startup 공고 이메일 크롤링 자동화
    @Transactional
    @Scheduled(cron = "0 30 3 * * ?") // 매일 새벽 3시 30분에 실행
    public void offCampusEmailAutoCrawling() {
        log.info("k-startup 공고 이메일 크롤링 자동화 시작");
        OpenData openData = new OpenData();
        openData.offCampusEmailCrawling(announcementRepository.findByAnnouncementTypeAndContactIsNull(AnnouncementType.OPEN_DATA));
    }
}
