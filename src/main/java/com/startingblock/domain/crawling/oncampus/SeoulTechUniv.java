package com.startingblock.domain.crawling.oncampus;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.announcement.domain.Keyword;
import com.startingblock.domain.announcement.domain.University;
import com.startingblock.domain.announcement.domain.repository.AnnouncementRepository;
import com.startingblock.domain.crawling.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.startingblock.domain.crawling.oncampus.constant.SeoulTechConstant.URL;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulTechConstant.TITLE1;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulTechConstant.TITLE2;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulTechConstant.DATE1;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulTechConstant.DATE2;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulTechConstant.VISIBLE;


@Service
@Slf4j
public class SeoulTechUniv extends CampusClassify implements CampusCrawling {

    private final AnnouncementRepository announcementRepository;
    public List<Announcement> announcementList = new ArrayList<>();

    public SeoulTechUniv(final AnnouncementRepository announcementRepository) {
        this.announcementRepository = announcementRepository;
    }

    @Override
    public List<Announcement> onCampusCrawling() {
        WebDriver driver = WebDriverManager.getDriver();
        try {
            driver.get(URL);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));  // 5초 동안 기다림
            // 아래 반복문은 1페이지를 크롤링한다.
            for (int i = 1; i <= 10; i++) {
                String titleXpath = TITLE1 + i + TITLE2;
                String dateXpath = DATE1 + i + DATE2;
                // title
                WebElement titleElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(titleXpath)));
                String title = titleElement.getText();
                log.info("title: " + title);

                // insertDate
                WebElement dateElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(dateXpath)));
                String insertDate = dateElement.getText();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate date = LocalDate.parse(insertDate, formatter);
                LocalDateTime dateTime = date.atStartOfDay(); // 자정 시간 추가
                log.info("date: " + dateTime);

                // detailUrl
                titleElement.click();
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(VISIBLE)));
                String detailUrl = driver.getCurrentUrl();
                log.info("href: " + detailUrl);

                //keyword
                Keyword keyword = super.classifyAnnouncement(title);
                log.info("keyword: " + keyword);

                if (announcementRepository.existsByDetailUrl(detailUrl)) { // 이미 존재하면 크롤링 중지, 존재하지 않는 것들만 크롤링한다.
                    break;
                }
                announcementList.add(CampusAnnouncementCreator.createCampusAnnouncement(title, dateTime, detailUrl, University.SEOUL_TECH, keyword));

                // 뒤로가기
                driver.navigate().back();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            WebDriverManager.closeDriver(); // 작업이 끝나면 드라이버를 닫음
        }
        return announcementList;
    }
}
