package com.startingblock.domain.crawling.oncampus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KonKukUniv implements CampusCrawling {

//    private final AnnouncementRepository announcementRepository;
    private CampusClassify campusClassify;

    @Override
    public void onCampusCrawling() {
        // WebDriver 옵션 설정
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("headless"); // 창 숨기기, ec2 환경에서는 설정해야함.
        options.addArguments("--start-maximized"); // 최대창
        options.addArguments("--disable-popup-blocking"); // 팝업창 무시하기

        WebDriver driver = new ChromeDriver(options);
        // 건국대학교 크롤링 url
        String url = "https://startup.konkuk.ac.kr/BoardList.do?menuSeq=43842&configSeq=51096";
        driver.get(url);

        String title1 = "//*[@id=\"container\"]/section/div[2]/div[2]/div/div[2]/table/tbody/tr[";
        String title2 = "]/td[2]/div/div/dl/dt/a";
        String date1 = "//*[@id=\"container\"]/section/div[2]/div[2]/div/div[2]/table/tbody/tr[";
        String date2 = "]/td[2]/div/div/div/span[2]";

        // 아래 반복문은 1페이지를 크롤링한다.
        for (int i = 1; i <= 10; i++) {
            String title = title1 + i + title2;
            String date = date1 + i + date2;
            // title
            WebElement titleElement = driver.findElement(By.xpath(title));
            log.info("title: " + titleElement.getText());
            // insertDate
            WebElement dateElement = driver.findElement(By.xpath(date));
            log.info("date: " + dateElement.getText());
            // detailUrl
            driver.findElement(By.xpath(title)).click();
            log.info("href: " + driver.getCurrentUrl());
            //keyword
            campusClassify.classifyAnnouncement(titleElement.getText());
            log.info("keyword: " + titleElement.getText());
            // 뒤로가기
            driver.navigate().back();
        }

        // 드라이버 종료
        driver.quit();
    }
}
