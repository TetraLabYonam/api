# 빌드 스테이지
FROM gradle:7.6-jdk17 AS builder

WORKDIR /app

# Gradle 캐시 최적화
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon

# 소스 코드 복사 및 빌드
COPY src ./src
RUN gradle clean build -x test --no-daemon

# 실행 스테이지
FROM openjdk:17-jdk-slim

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크 (Spring Boot Actuator 사용 시)
# HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#   CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]