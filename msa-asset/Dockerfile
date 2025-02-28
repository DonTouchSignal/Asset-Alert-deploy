FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY build/libs/msa-asset-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]