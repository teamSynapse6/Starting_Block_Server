# Debian 기반의 slim 이미지 사용
FROM openjdk:17-slim

# Chrome 및 ChromeDriver 버전 명시
ENV CHROME_VERSION="115.0.5790.102-1"
ENV CHROMEDRIVER_VERSION="115.0.5790.102"

# 필요한 패키지를 apt-get으로 설치 및 Chrome 의존성 추가 설치
RUN apt-get update && apt-get install -y wget unzip curl fonts-liberation \
    libasound2 libatk-bridge2.0-0 libatk1.0-0 libcups2 libdrm2 libgbm1 \
    libgtk-3-0 libnspr4 libnss3 libx11-xcb1 libxcomposite1 libxdamage1 \
    libxrandr2 xdg-utils \
    && wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y ./google-chrome-stable_current_amd64.deb \
    && rm ./google-chrome-stable_current_amd64.deb

# ChromeDriver 설치 (환경 변수 없이 버전 직접 명시)
RUN wget -O /tmp/chromedriver.zip "https://chromedriver.storage.googleapis.com/115.0.5790.102/chromedriver_linux64.zip" \
    || curl -o /tmp/chromedriver.zip "https://chromedriver.storage.googleapis.com/115.0.5790.102/chromedriver_linux64.zip" \
    && unzip /tmp/chromedriver.zip -d /usr/local/bin/ \
    && chmod +x /usr/local/bin/chromedriver \
    && rm /tmp/chromedriver.zip

# Xvfb 설치 및 환경 변수 설정
RUN apt-get install -y xvfb
ENV DISPLAY=:99

# Chrome 및 ChromeDriver 경로 환경 변수 설정
ENV CHROME_BIN=/usr/bin/google-chrome
ENV CHROME_DRIVER=/usr/local/bin/chromedriver

# 애플리케이션 JAR 복사
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 포트 노출
EXPOSE 8080

# 애플리케이션 시작 명령어
ENTRYPOINT ["sh", "-c", "Xvfb :99 -ac & java -jar /app.jar"]
