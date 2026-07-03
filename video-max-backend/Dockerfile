FROM maven:3.9.6-eclipse-temurin-21-alpine

WORKDIR /app

# Download dependencies before copying source (cache layer)
COPY pom.xml .
RUN mvn dependency:resolve -q --no-transfer-progress

COPY src ./src

EXPOSE 8080

# Container stays alive; app is started manually via:
# docker compose exec spring-boot-app mvn spring-boot:run -Dspring-boot.run.profiles=dev
CMD ["tail", "-f", "/dev/null"]
