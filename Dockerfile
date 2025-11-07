FROM eclipse-temurin:17-jre-alpine

COPY build/libs/*SNAPSHOT.jar /app.jar

ENTRYPOINT ["java", "-jgar", "/app.jar"]