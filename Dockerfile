# syntax=docker/dockerfile:1
# One image for the demo (Spec §4.2, AR-001): build React, put it in Spring Boot's classpath /static,
# package one jar, run it on a JRE as a non-root user. Image tags match the Convention §2 versions.

FROM node:24.22.2-bookworm-slim AS web
WORKDIR /src/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:21.0.13_9-jdk AS api
WORKDIR /src/api
COPY api/.mvn/ .mvn/
COPY api/mvnw api/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY api/src/ src/
COPY data/ /src/data/
COPY --from=web /src/web/dist/ src/main/resources/static/
# Tests already ran on the PR (./mvnw verify). Pass --build-arg MAVEN_ARGS= to run them here too.
ARG MAVEN_ARGS="-DskipTests"
RUN ./mvnw -B -q package ${MAVEN_ARGS}

FROM eclipse-temurin:21.0.13_9-jre
RUN groupadd --system app && useradd --system --gid app --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=api /src/api/target/api-*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
