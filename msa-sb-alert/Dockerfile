FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY build/libs/msa-sb-alert-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]