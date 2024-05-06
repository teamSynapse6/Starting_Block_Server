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
public class SeoulUniv implements CampusCrawling {

    @Override
    public void onCampusCrawling() {
        // WebDriver 옵션 설정
        ChromeOptions options = new ChromeOptions();
//        options.addArguments("headless"); // 창 숨기기, ec2 환경에서는 설정해야함.
        options.addArguments("--start-maximized"); // 최대창
        options.addArguments("--disable-popup-blocking"); // 팝업창 무시하기

        WebDriver driver = new ChromeDriver(options);
        // 서울대 크롤링 url
        String url = "https://startup.snu.ac.kr/front/lounge/notice";
        driver.get(url);

        String title1 = "//*[@id=\"wrapper\"]/div[1]/div/div/div[2]/table/tbody/tr[";
        String title2 = "]/td[2]/div[1]/a";
        String date1 = "//*[@id=\"wrapper\"]/div[1]/div/div/div[2]/table/tbody/tr[";
        String date2 = "]/td[5]";
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

            // 뒤로가기
            driver.navigate().back();
        }

        // 드라이버 종료
        driver.quit();
    }
}
//*[@id="content"]/div/div[2]/div/ul/li[1]/a
//*[@id="content"]/div/div[2]/div/ul/li[2]/a
//*[@id="content"]/div/div[2]/div/ul/li[6]/a

//*[@id="content"]/div/div[2]/div/ul/li[1]/a/strong[1]
//*[@id="content"]/div/div[2]/div/ul/li[5]/a/strong[1]
//*[@id="content"]/div/div[2]/div/ul/li[6]/a/strong[1]
