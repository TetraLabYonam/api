# 배포 가이드 (Quick Start)

이 문서는 빠른 배포를 위한 간단한 가이드입니다. 상세한 내용은 [CI_CD_SETUP_GUIDE.md](CI_CD_SETUP_GUIDE.md)를 참조하세요.

## 🚀 빠른 시작

### 1. GitHub Secrets 설정

GitHub 리포지토리 → Settings → Secrets and variables → Actions에서 다음 값들을 추가:

```
EC2_HOST=13.125.xxx.xxx
EC2_USER=ubuntu
EC2_SSH_KEY=(키페어 파일 전체 내용)
DB_PASSWORD=your-db-password
```

### 2. EC2 서버 초기 설정

```bash
# EC2 접속
ssh -i your-key.pem ubuntu@your-ec2-ip

# 초기 설정 스크립트 실행
sudo apt update && sudo apt upgrade -y
sudo apt install openjdk-17-jdk mariadb-server -y

# 디렉토리 생성
sudo mkdir -p /opt/app /var/log/app
sudo chown ubuntu:ubuntu /opt/app /var/log/app

# MariaDB 설정
sudo mysql -u root -p
```

```sql
CREATE DATABASE queueapp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'queue'@'localhost' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON queueapp.* TO 'queue'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 3. systemd 서비스 등록

```bash
sudo nano /etc/systemd/system/springboot-app.service
```

다음 내용 입력:

```ini
[Unit]
Description=Spring Boot Application
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -jar /opt/app/attempt-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=10

Environment="SPRING_PROFILES_ACTIVE=prod"

StandardOutput=append:/var/log/app/application.log
StandardError=append:/var/log/app/error.log

[Install]
WantedBy=multi-user.target
```

서비스 활성화:

```bash
sudo systemctl daemon-reload
sudo systemctl enable springboot-app
```

### 4. 배포

main 브랜치에 push하면 자동으로 배포됩니다:

```bash
git add .
git commit -m "Deploy to EC2"
git push origin main
```

GitHub Actions가 자동으로:
1. 코드 빌드
2. EC2에 배포
3. 서비스 재시작

### 5. 확인

```bash
# 서비스 상태 확인
sudo systemctl status springboot-app

# 로그 확인
sudo journalctl -u springboot-app -f
```

브라우저에서 접속: `http://your-ec2-ip:8080`

---

## 🐳 Docker 사용 시

### EC2에 Docker 설치

```bash
sudo apt install docker.io docker-compose -y
sudo usermod -aG docker $USER
newgrp docker
```

### 배포

```bash
cd /opt/app
git clone https://github.com/TetraLabYonam/api.git .

# .env 파일 생성
nano .env
```

```env
DB_PASSWORD=your-password
DB_ROOT_PASSWORD=your-root-password
```

### 실행

```bash
docker-compose up -d
docker-compose logs -f
```

---

## 📝 주요 명령어

```bash
# 서비스 관리
sudo systemctl start springboot-app
sudo systemctl stop springboot-app
sudo systemctl restart springboot-app
sudo systemctl status springboot-app

# 로그 확인
sudo journalctl -u springboot-app -n 100
tail -f /var/log/app/application.log

# Docker
docker-compose up -d
docker-compose down
docker-compose logs -f
docker-compose restart
```

---

## 🔧 트러블슈팅

### 포트 8080이 이미 사용 중

```bash
sudo lsof -i :8080
sudo kill -9 <PID>
```

### DB 연결 실패

```bash
sudo systemctl status mariadb
mysql -u queue -p queueapp
```

### 메모리 부족

systemd 서비스 파일 수정:

```ini
ExecStart=/usr/bin/java -Xmx512m -Xms256m -jar /opt/app/attempt-0.0.1-SNAPSHOT.jar
```

---

상세한 가이드는 [CI_CD_SETUP_GUIDE.md](CI_CD_SETUP_GUIDE.md)를 참조하세요.