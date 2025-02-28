FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY build/libs/alert-service-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]