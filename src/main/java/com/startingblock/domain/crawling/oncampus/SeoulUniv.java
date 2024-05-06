package com.startingblock.domain.crawling.oncampus;

import com.startingblock.domain.crawling.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.URL;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.TITLE1;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.TITLE2;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.DATE1;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.DATE2;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.NOTIFICATION1;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.NOTIFICATION2;
import static com.startingblock.domain.crawling.oncampus.constant.SeoulConstant.VISIBLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeoulUniv extends CampusClassify implements CampusCrawling {

    @Override
    public void onCampusCrawling() {
        WebDriver driver = WebDriverManager.getDriver();
        try {
            driver.get(URL);

            int notificationCount = 0;

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));  // 5초 동안 기다림

            // 아래 반복문은 1페이지를 크롤링한다.
            for (int i = 1; notificationCount < 12; i++) {
                String titleXpath = TITLE1 + i + TITLE2;
                String dateXpath = DATE1 + i + DATE2;
                String notificationXpath = NOTIFICATION1 + i + NOTIFICATION2;

                WebElement notificationElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(notificationXpath)));
                String notification = notificationElement.getText();
                if (Objects.equals(notification, "공지")) continue;
                notificationCount++;

                // title
                WebElement titleElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(titleXpath)));
                String title = titleElement.getText();
                log.info("title: " + title);

                // insertDate
                WebElement dateElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(dateXpath)));
                String date = dateElement.getText().substring(0, 10);
                log.info("date: " + date);

                // detailUrl
                titleElement.click();
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(VISIBLE)));
                String detailUrl = driver.getCurrentUrl();
                log.info("href: " + detailUrl);

                // keyword
                String keyword = super.classifyAnnouncement(title);
                log.info("keyword: " + keyword);

                // 뒤로가기
                driver.navigate().back();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            WebDriverManager.closeDriver(); // 작업이 끝나면 드라이버를 닫음
        }
    }
}
