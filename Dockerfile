FROM maven:3.9-amazoncorretto-17-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
# The CI workflow runs the MySQL Testcontainers suite before this image is
# built. skipTests still compiles the test source here, but avoids trying to
# launch a sibling Docker container from inside a Docker image build.
RUN mvn -B -DskipTests clean package


FROM eclipse-temurin:17-jre-jammy

# Read driver schedule screenshots locally; no external OCR credentials required.
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
