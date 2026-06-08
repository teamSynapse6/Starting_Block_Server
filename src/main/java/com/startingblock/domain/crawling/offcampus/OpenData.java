package com.startingblock.domain.crawling.offcampus;

import com.startingblock.domain.announcement.domain.Announcement;
import com.startingblock.domain.crawling.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.startingblock.domain.crawling.offcampus.constant.OpenDataConstant.EMAIL_XPATH;
import static com.startingblock.domain.crawling.offcampus.constant.OpenDataConstant.EMAIL_REGEX;
import static com.startingblock.domain.crawling.offcampus.constant.OpenDataConstant.ONCE_POPUP;
import static com.startingblock.domain.crawling.offcampus.constant.OpenDataConstant.CLOSE_POPUP;

@Service
@Slf4j
public class OpenData implements OffCampusCrawling {

    @Override
    @Transactional
    public void offCampusEmailCrawling(List<Announcement> announcementList) {
        WebDriver driver = WebDriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));  // 5초 동안 기다림

        try {
            boolean firstAccess = true;
            for (Announcement announcement : announcementList) {
                driver.get(announcement.getDetailUrl());
                log.info("url: " + announcement.getDetailUrl());
                if (firstAccess) {
                    closePopup(driver); // 팝업창 닫기
                    firstAccess = false;
                }
                try {
                    String email = findEmail(driver, wait);
                    if (email != null) { // 이메일이 존재하는 경우
                        log.info("email: {}", email);
                        announcement.updateContact(email);
                    } else{
                        log.info("이메일이 존재하지 않음.");
                    }
                } catch (Exception e) {
                    log.warn("이메일 크롤링 실패 announcementId={}, url={}", announcement.getId(), announcement.getDetailUrl(), e);
                }
            }
        } catch (Exception e) {
            log.error("K-Startup 이메일 크롤링 드라이버 실행 실패", e);
        } finally {
            WebDriverManager.closeDriver(); // 작업이 끝나면 드라이버를 닫음
        }
    }

    private String findEmail(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement emailElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(EMAIL_XPATH)));
            log.info("emailElement: {}", emailElement.getText());
            return extractEmail(emailElement.getText());
        } catch (Exception exception) {
            log.warn("이메일 XPath 탐색 실패, body fallback 시도: {}", exception.getMessage());
        }

        try {
            WebElement body = driver.findElement(By.tagName("body"));
            return extractEmail(body.getText());
        } catch (Exception exception) {
            log.warn("이메일 body fallback 실패: {}", exception.getMessage());
            return null;
        }
    }

    private String extractEmail(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile(EMAIL_REGEX);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    public void closePopup(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofMillis(500));  // 0.5초 동안 기다림
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(ONCE_POPUP))).click(); // 한 번만 보기 옵션 클릭
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(CLOSE_POPUP))).click();   // 팝업창 닫기
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
    }
}
