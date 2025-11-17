# AWS EC2 CI/CD 구축 가이드

Spring Boot 프로젝트를 AWS EC2에 자동 배포하기 위한 CI/CD 파이프라인 구축 가이드

## 📋 목차

1. [개요](#개요)
2. [사전 준비사항](#사전-준비사항)
3. [AWS EC2 인스턴스 설정](#aws-ec2-인스턴스-설정)
4. [GitHub Actions CI/CD 설정](#github-actions-cicd-설정)
5. [배포 스크립트 작성](#배포-스크립트-작성)
6. [환경 변수 관리](#환경-변수-관리)
7. [Docker를 사용한 배포](#docker를-사용한-배포)
8. [트러블슈팅](#트러블슈팅)

---

## 개요

### CI/CD 파이프라인 흐름

```
GitHub Push → GitHub Actions → Build & Test → Deploy to EC2 → Restart Application
```

### 사용 기술 스택

- **CI/CD**: GitHub Actions
- **서버**: AWS EC2 (Ubuntu 22.04 LTS)
- **빌드 도구**: Gradle
- **런타임**: Java 17
- **프로세스 관리**: systemd
- **선택사항**: Docker, Nginx

---

## 사전 준비사항

### 1. AWS 계정 및 EC2 인스턴스
- AWS 계정 생성
- EC2 인스턴스 생성 (t2.micro 이상 권장)
- 보안 그룹 설정 (포트 8080, 22 오픈)

### 2. 도메인 (선택사항)
- 도메인 구매 및 EC2 퍼블릭 IP 연결

### 3. GitHub 리포지토리
- 프로젝트가 Push된 GitHub 리포지토리

---

## AWS EC2 인스턴스 설정

### 1. EC2 인스턴스 생성

#### AWS Console에서 설정
```
1. EC2 대시보드 → "인스턴스 시작" 클릭
2. AMI 선택: Ubuntu Server 22.04 LTS
3. 인스턴스 유형: t2.small 이상 권장
4. 키 페어 생성/선택 (다운로드 후 안전하게 보관)
5. 보안 그룹 설정:
   - SSH (22): 내 IP
   - HTTP (80): 0.0.0.0/0
   - HTTPS (443): 0.0.0.0/0
   - Custom TCP (8080): 0.0.0.0/0
```

### 2. SSH 접속

```bash
# 키 페어 권한 설정
chmod 400 your-key.pem

# EC2 접속
ssh -i your-key.pem ubuntu@your-ec2-public-ip
```

### 3. EC2 초기 설정

```bash
# 시스템 업데이트
sudo apt update && sudo apt upgrade -y

# Java 17 설치
sudo apt install openjdk-17-jdk -y
java -version

# Git 설치
sudo apt install git -y

# Gradle 설치 (선택사항, gradlew 사용 가능)
sudo apt install gradle -y
```

### 4. 애플리케이션 디렉토리 생성

```bash
# 애플리케이션 디렉토리
sudo mkdir -p /opt/app
sudo chown ubuntu:ubuntu /opt/app

# 로그 디렉토리
sudo mkdir -p /var/log/app
sudo chown ubuntu:ubuntu /var/log/app
```

### 5. MySQL/MariaDB 설치

```bash
# MariaDB 설치
sudo apt install mariadb-server -y

# MariaDB 보안 설정
sudo mysql_secure_installation

# MariaDB 접속
sudo mysql -u root -p

# 데이터베이스 및 사용자 생성
CREATE DATABASE queueapp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'queue'@'localhost' IDENTIFIED BY 'your-secure-password';
GRANT ALL PRIVILEGES ON queueapp.* TO 'queue'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

---

## GitHub Actions CI/CD 설정

### 1. GitHub Secrets 설정

GitHub 리포지토리 → Settings → Secrets and variables → Actions

다음 Secrets 추가:

| Secret 이름 | 설명 | 예시 값 |
|------------|------|---------|
| `EC2_HOST` | EC2 퍼블릭 IP | `13.125.xxx.xxx` |
| `EC2_USER` | SSH 사용자 | `ubuntu` |
| `EC2_SSH_KEY` | SSH 개인키 내용 | 키 페어 파일 전체 내용 |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | `your-db-password` |

### 2. GitHub Actions 워크플로우 파일 생성

`.github/workflows/deploy.yml` 파일:

```yaml
name: Deploy to EC2

on:
  push:
    branches:
      - main  # main 브랜치에 push 시 자동 배포

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      # 1. 코드 체크아웃
      - name: Checkout code
        uses: actions/checkout@v3

      # 2. JDK 17 설정
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      # 3. Gradle 캐시 설정
      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      # 4. Gradle 빌드
      - name: Build with Gradle
        run: |
          chmod +x ./gradlew
          ./gradlew clean build -x test

      # 5. EC2로 JAR 파일 전송
      - name: Deploy to EC2
        uses: appleboy/scp-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          source: "build/libs/*.jar"
          target: "/opt/app"
          strip_components: 2

      # 6. EC2에서 애플리케이션 재시작
      - name: Restart application
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            sudo systemctl restart springboot-app
            sudo systemctl status springboot-app
```

### 3. 테스트 포함 버전 (권장)

테스트를 포함한 완전한 CI/CD 워크플로우:

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    name: Test
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Run tests
        run: |
          chmod +x ./gradlew
          ./gradlew test

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: build/test-results/

  build-and-deploy:
    name: Build and Deploy
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Build with Gradle
        run: |
          chmod +x ./gradlew
          ./gradlew clean build -x test

      - name: Deploy to EC2
        uses: appleboy/scp-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          source: "build/libs/*.jar"
          target: "/opt/app"
          strip_components: 2

      - name: Restart application
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            sudo systemctl restart springboot-app
            sleep 5
            sudo systemctl status springboot-app
```

---

## 배포 스크립트 작성

### 1. systemd 서비스 파일 생성

EC2에서 `/etc/systemd/system/springboot-app.service` 파일 생성:

```bash
sudo nano /etc/systemd/system/springboot-app.service
```

다음 내용 입력:

```ini
[Unit]
Description=Spring Boot Application
After=syslog.target network.target

[Service]
User=ubuntu
Type=simple
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -jar /opt/app/attempt-0.0.1-SNAPSHOT.jar
SuccessExitStatus=143
Restart=always
RestartSec=10

# 환경 변수 설정
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SERVER_PORT=8080"

# 로그 설정
StandardOutput=append:/var/log/app/application.log
StandardError=append:/var/log/app/error.log

# 리소스 제한
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

### 2. 서비스 활성화 및 시작

```bash
# systemd 데몬 리로드
sudo systemctl daemon-reload

# 서비스 활성화 (부팅 시 자동 시작)
sudo systemctl enable springboot-app

# 서비스 시작
sudo systemctl start springboot-app

# 서비스 상태 확인
sudo systemctl status springboot-app

# 로그 확인
sudo journalctl -u springboot-app -f
```

### 3. 서비스 관리 명령어

```bash
# 서비스 시작
sudo systemctl start springboot-app

# 서비스 중지
sudo systemctl stop springboot-app

# 서비스 재시작
sudo systemctl restart springboot-app

# 서비스 상태 확인
sudo systemctl status springboot-app

# 실시간 로그 확인
sudo journalctl -u springboot-app -f

# 최근 100줄 로그
sudo journalctl -u springboot-app -n 100
```

---

## 환경 변수 관리

### 1. application-prod.yml 파일 생성

EC2에 프로덕션 환경 설정 파일 생성:

```bash
# 설정 파일 디렉토리 생성
sudo mkdir -p /opt/app/config
sudo chown ubuntu:ubuntu /opt/app/config

# 프로덕션 설정 파일 생성
nano /opt/app/config/application-prod.yml
```

내용:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/queueapp?useUnicode=true&characterEncoding=utf8
    username: queue
    password: ${DB_PASSWORD}  # 환경 변수에서 주입
    driver-class-name: org.mariadb.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate  # 프로덕션에서는 validate 사용
    show-sql: false
    properties:
      hibernate:
        format_sql: false
    open-in-view: false

logging:
  level:
    root: INFO
    com.example.attempt: INFO
  file:
    name: /var/log/app/application.log
    max-size: 10MB
    max-history: 30
```

### 2. 환경 변수 파일 생성

보안이 필요한 정보는 환경 변수 파일로 관리:

```bash
sudo nano /opt/app/.env
```

내용:

```bash
DB_PASSWORD=your-secure-password
JWT_SECRET=your-jwt-secret-key
```

권한 설정:

```bash
sudo chmod 600 /opt/app/.env
sudo chown ubuntu:ubuntu /opt/app/.env
```

### 3. systemd 서비스에 환경 변수 적용

`/etc/systemd/system/springboot-app.service` 수정:

```ini
[Service]
# 환경 변수 파일 로드
EnvironmentFile=/opt/app/.env

# Spring Profile 설정
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_CONFIG_LOCATION=/opt/app/config/application-prod.yml"

ExecStart=/usr/bin/java -jar /opt/app/attempt-0.0.1-SNAPSHOT.jar
```

---

## Docker를 사용한 배포

### 1. Dockerfile 작성

프로젝트 루트에 `Dockerfile` 생성:

```dockerfile
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

# 헬스체크
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 2. docker-compose.yml 작성

```yaml
version: '3.8'

services:
  app:
    build: .
    container_name: springboot-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mariadb://db:3306/queueapp
      - SPRING_DATASOURCE_USERNAME=queue
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
    depends_on:
      - db
    restart: unless-stopped
    networks:
      - app-network

  db:
    image: mariadb:10.11
    container_name: mariadb
    environment:
      - MARIADB_ROOT_PASSWORD=${DB_ROOT_PASSWORD}
      - MARIADB_DATABASE=queueapp
      - MARIADB_USER=queue
      - MARIADB_PASSWORD=${DB_PASSWORD}
    volumes:
      - db-data:/var/lib/mysql
    ports:
      - "3306:3306"
    restart: unless-stopped
    networks:
      - app-network

networks:
  app-network:
    driver: bridge

volumes:
  db-data:
```

### 3. .dockerignore 파일

```
.git
.gitignore
.gradle
build
*.md
.idea
.vscode
```

### 4. EC2에 Docker 설치

```bash
# Docker 설치
sudo apt update
sudo apt install docker.io -y
sudo systemctl start docker
sudo systemctl enable docker

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 현재 사용자를 docker 그룹에 추가
sudo usermod -aG docker $USER
newgrp docker

# 확인
docker --version
docker-compose --version
```

### 5. Docker 기반 GitHub Actions 워크플로우

`.github/workflows/docker-deploy.yml`:

```yaml
name: Docker Deploy to EC2

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Deploy to EC2 with Docker
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ${{ secrets.EC2_USER }}
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /opt/app
            git pull origin main
            docker-compose down
            docker-compose up -d --build
            docker-compose logs -f --tail=50
```

---

## 트러블슈팅

### 1. 포트가 이미 사용 중

```bash
# 8080 포트 사용 중인 프로세스 확인
sudo lsof -i :8080

# 프로세스 종료
sudo kill -9 <PID>
```

### 2. 애플리케이션이 시작되지 않음

```bash
# 로그 확인
sudo journalctl -u springboot-app -n 100 --no-pager

# JAR 파일 확인
ls -lh /opt/app/*.jar

# 수동 실행 테스트
cd /opt/app
java -jar attempt-0.0.1-SNAPSHOT.jar
```

### 3. 데이터베이스 연결 실패

```bash
# MariaDB 상태 확인
sudo systemctl status mariadb

# MariaDB 연결 테스트
mysql -u queue -p queueapp

# MariaDB 로그 확인
sudo tail -f /var/log/mysql/error.log
```

### 4. 메모리 부족

```bash
# 메모리 사용량 확인
free -h

# JVM 힙 메모리 제한 (systemd 서비스 파일)
ExecStart=/usr/bin/java -Xmx512m -Xms256m -jar /opt/app/attempt-0.0.1-SNAPSHOT.jar
```

### 5. GitHub Actions 실패

- **SSH 연결 실패**: EC2 보안 그룹에서 SSH 포트(22) 확인
- **권한 에러**: EC2에서 `/opt/app` 디렉토리 권한 확인
- **빌드 실패**: 로컬에서 `./gradlew clean build` 테스트

---

## 보안 권장사항

### 1. 방화벽 설정 (UFW)

```bash
# UFW 설치 및 활성화
sudo apt install ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8080/tcp
sudo ufw enable
```

### 2. Nginx 리버스 프록시 (선택사항)

```bash
# Nginx 설치
sudo apt install nginx -y

# 설정 파일 생성
sudo nano /etc/nginx/sites-available/springboot
```

Nginx 설정:

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

활성화:

```bash
sudo ln -s /etc/nginx/sites-available/springboot /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

### 3. SSL/TLS 인증서 (Let's Encrypt)

```bash
# Certbot 설치
sudo apt install certbot python3-certbot-nginx -y

# SSL 인증서 발급
sudo certbot --nginx -d your-domain.com

# 자동 갱신 설정 확인
sudo certbot renew --dry-run
```

---

## 모니터링 및 로그 관리

### 1. 로그 로테이션

```bash
sudo nano /etc/logrotate.d/springboot-app
```

내용:

```
/var/log/app/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0640 ubuntu ubuntu
    sharedscripts
    postrotate
        systemctl reload springboot-app > /dev/null 2>&1 || true
    endscript
}
```

### 2. 디스크 공간 모니터링

```bash
# 디스크 사용량 확인
df -h

# 큰 파일/디렉토리 찾기
du -sh /var/log/* | sort -rh | head -10
```

---

## 정리

이제 완전한 CI/CD 파이프라인이 구축되었습니다!

### 배포 프로세스

1. 코드 수정 및 GitHub에 push
2. GitHub Actions 자동 실행
   - 테스트 실행
   - 빌드
   - EC2로 배포
   - 애플리케이션 재시작
3. 브라우저에서 확인: `http://your-ec2-ip:8080`

### 주요 명령어 요약

```bash
# 서비스 관리
sudo systemctl status springboot-app
sudo systemctl restart springboot-app

# 로그 확인
sudo journalctl -u springboot-app -f
tail -f /var/log/app/application.log

# Docker (선택)
docker-compose up -d
docker-compose logs -f
docker-compose down
```

### 추가 개선사항

- [ ] 모니터링 도구 설정 (Prometheus, Grafana)
- [ ] 백업 자동화
- [ ] Blue-Green 배포
- [ ] 로드 밸런서 설정 (다중 인스턴스)

---

**문의사항이나 문제가 있을 경우 이슈를 등록해주세요!**