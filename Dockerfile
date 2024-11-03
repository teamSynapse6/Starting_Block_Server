FROM openjdk:17

# Chrome 및 ChromeDriver 설치
ENV CHROME_VERSION="115.0.5790.102-1"
ENV CHROMEDRIVER_VERSION="115.0.5790.102"

# 필요한 패키지를 apk로 설치
RUN apk update && apk add --no-cache wget unzip bash \
    && wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apk add --no-cache ./google-chrome-stable_current_amd64.deb \
    && rm ./google-chrome-stable_current_amd64.deb

# ChromeDriver 설치
RUN wget -O /tmp/chromedriver.zip "https://chromedriver.storage.googleapis.com/$CHROMEDRIVER_VERSION/chromedriver_linux64.zip" \
    && unzip /tmp/chromedriver.zip -d /usr/local/bin/ \
    && chmod +x /usr/local/bin/chromedriver \
    && rm /tmp/chromedriver.zip

# Xvfb 설치 및 환경 변수 설정
RUN apk add --no-cache xvfb
ENV DISPLAY=:99

# Chrome 및 ChromeDriver 경로 환경 변수 설정
ENV CHROME_BIN=/usr/bin/google-chrome
ENV CHROME_DRIVER=/usr/local/bin/chromedriver

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "Xvfb :99 -ac & java -jar /app.jar"]
