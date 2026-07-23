FROM gradle:8.14-jdk21 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle* gradle.properties* ./
COPY src ./src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
RUN apk add --no-cache wget
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
