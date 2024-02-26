package com.startingblock.domain.crawling;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class CrawlingSerivce {

    // k-startup 모집중 공고 url
    private static final String kstartupUrl = "https://www.k-startup.go.kr/web/contents/bizpbanc-ongoing.do";

    private static ArrayList<String> contentList = new ArrayList<>();

    public static void offCampusCrawling() {

        // WebDriver 옵션 설정
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("headless"); // 창 숨기기
        options.addArguments("--start-maximized"); // 최대창
        options.addArguments("--disable-popup-blocking"); // 팝업창 무시하기
        //

        WebDriver driver = new ChromeDriver(options);
        driver.get(kstartupUrl);

        // 팝업창 직접 닫기
        try {
            String oneTimePopup = "//*[@id=\"t_c_2955\"]";
            driver.findElement(By.xpath(oneTimePopup)).click(); // 한 번만 보기 옵션 클릭
            String closePopup = "//*[@id=\"cmn_pop_pbancList_010\"]/div[2]/button";
            driver.findElement(By.xpath(closePopup)).click();   // 팝업창 닫기
        } catch (Exception e) {
            log.warn(e.getMessage());
        }

        // click 이벤트 발생
//        String xpath = "//*[@id=\"bizPbancList\"]/ul/li[1]/div/div[1]/div[2]/a/div/p";
        String list1 = "//*[@id=\"bizPbancList\"]/ul/li[";
        String list2 = "]/div/div[1]/div[2]/a/div/p";
        for (int i = 1; i <= 15; i++) {
            String list = list1 + i + list2;
            // 공고 클릭
            driver.findElement(By.xpath(list)).click();
            //*[@id="bizPbancList"]/ul/li[10]/div/div[1]/div[2]/a/div/p
            //*[@id="bizPbancList"]/ul/li[10]/div/div[1]/div[2]/a/div/p
            // 본문(content) 크롤링
            String content = driver.findElement(By.xpath("//*[@id=\"contentViewHtml\"]/div/div/div/div[1]/div[3]/div/p[1]")).getText();
            contentList.add(content);
            System.out.println(contentList.get(i - 1));

            // 이메일(contact) 크롤링 -> 없으면 "Na"로 반환 (코드 아직 없음)
            try {
                String emailPath = "//*[@id=\"contentViewHtml\"]/div/div/div/div[2]/div[5]/ul/li[1]/div/p";
                WebElement emailElement = driver.findElement(By.xpath(emailPath));

                Pattern pattern = Pattern.compile("[a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}"); // 이메을 찾는 정규표현식
                Matcher matcher = pattern.matcher(emailElement.getText());

                if (matcher.find()) { // 이메일이 존재하는 경우
                    String email = matcher.group();
                    System.out.println(email);
                }
            } catch (Exception e) {
//                log.warn(e.getMessage());
            }


            // 뒤로가기 (메인 페이지로 이동)
            driver.navigate().back();
        }

        // 2번페이지로 이동 테스트 합격
        String page2 = "//*[@id=\"bizPbancList\"]/div/a[4]";
        driver.findElement(By.xpath(page2)).click();

        // 드라이버 종료
        driver.quit();
    }

    // 건국대학교 크롤링
    public static void onCampusCrawling() {

        // WebDriver 옵션 설정
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("headless"); // 창 숨기기
        options.addArguments("--start-maximized"); // 최대창
        options.addArguments("--disable-popup-blocking"); // 팝업창 무시하기

        WebDriver driver = new ChromeDriver(options);
        // 건국대학교 크롤링 url
        String url = "https://startup.konkuk.ac.kr/BoardList.do?menuSeq=43842&configSeq=51096";
        driver.get(url);

        WebElement titleElement = driver.findElement(By.xpath("//*[@id=\"container\"]/section/div[2]/div[2]/div/div[2]/table/tbody/tr[1]/td[2]/div/div/dl/dt"));
        log.info("title: " + titleElement.getText());
        WebElement dateElement = driver.findElement(By.xpath("//*[@id=\"container\"]/section/div[2]/div[2]/div/div[2]/table/tbody/tr[1]/td[2]/div/div/div/span[2]"));
        log.info("date: " + dateElement.getText());

        // 드라이버 종료
        driver.quit();
    }
}
