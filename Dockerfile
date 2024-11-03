FROM openjdk:17

# Chrome과 ChromeDriver 버전 고정
ENV CHROME_VERSION="115.0.5790.102-1"
ENV CHROMEDRIVER_VERSION="115.0.5790.102"

# Chrome 설치
RUN apt-get update && apt-get install -y wget unzip \
    && wget https://dl.google.com/linux/deb/pool/main/g/google-chrome-stable/google-chrome-stable_${CHROME_VERSION}_amd64.deb \
    && apt-get install -y ./google-chrome-stable_${CHROME_VERSION}_amd64.deb \
    && rm ./google-chrome-stable_${CHROME_VERSION}_amd64.deb

# ChromeDriver 설치
RUN wget -O /tmp/chromedriver.zip "https://chromedriver.storage.googleapis.com/$CHROMEDRIVER_VERSION/chromedriver_linux64.zip" \
    && unzip /tmp/chromedriver.zip -d /usr/local/bin/ \
    && chmod +x /usr/local/bin/chromedriver \
    && rm /tmp/chromedriver.zip

# Xvfb 설치 및 환경 변수 설정
RUN apt-get install -y xvfb
ENV DISPLAY=:99

# Chrome과 ChromeDriver 경로 환경 변수 설정
ENV CHROME_BIN=/usr/bin/google-chrome
ENV CHROME_DRIVER=/usr/local/bin/chromedriver

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "Xvfb :99 -ac & java -jar /app.jar"]
