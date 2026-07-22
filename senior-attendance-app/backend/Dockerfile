# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
# MaxRAMPercentage lets the JVM heap scale with the container's memory limit
# (set via docker-compose mem_limit) instead of a fixed -Xmx.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "app.jar"]
