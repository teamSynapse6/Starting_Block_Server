FROM openjdk:17

ARG JAR_FILE=build/libs/*.jar
ENV TZ=Asia/Seoul
RUN apt-get install -y tzdata
COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
