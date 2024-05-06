package com.startingblock.domain.crawling.oncampus;

import com.startingblock.domain.crawling.WebDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;

import static com.startingblock.domain.crawling.oncampus.constant.HUFSConstant.DATE1;
import static com.startingblock.domain.crawling.oncampus.constant.HUFSConstant.DATE2;
import static com.startingblock.domain.crawling.oncampus.constant.HUFSConstant.TITLE1;
import static com.startingblock.domain.crawling.oncampus.constant.HUFSConstant.TITLE2;
import static com.startingblock.domain.crawling.oncampus.constant.HUFSConstant.URL;
import static com.startingblock.domain.crawling.oncampus.constant.HUFSConstant.VISIBLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class HUFSUniv extends CampusClassify implements CampusCrawling {

    @Override
    public void onCampusCrawling() {
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
                log.info("date: " + insertDate);

                // detailUrl
                titleElement.click();
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(VISIBLE)));
                String detailUrl = driver.getCurrentUrl();
                log.info("href: " + detailUrl);

                //keyword
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
