package com.startingblock.domain.crawling;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class WebDriverManager {

    private static WebDriver driver;

    private WebDriverManager() { } // 생성자를 private로 설정하여 외부에서 인스턴스 생성을 막음

    public static WebDriver getDriver() {
        if (driver == null) {
            synchronized (WebDriverManager.class) {
                if (driver == null) {
                    ChromeOptions options = new ChromeOptions();
                    options.addArguments("headless"); // 창 숨기기, ec2 환경에서는 설정해야함.
                    options.addArguments("--start-maximized"); // 최대창
                    options.addArguments("window-size=1920,1000");
                    options.addArguments("--disable-popup-blocking"); // 팝업창 무시하기
                    driver = new ChromeDriver(options);
                }
            }
        }
        return driver;
    }

    public static void closeDriver() {
        if (driver != null) {
            driver.quit();
            driver = null; // 드라이버 종료 후 null로 설정하여 다음 호출 시 새로운 드라이버 초기화 가능
        }
    }
}
