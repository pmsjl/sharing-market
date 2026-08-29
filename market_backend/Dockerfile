FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/target/market_backend-*.jar /app/app.jar

USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
