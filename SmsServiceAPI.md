# 개요
- 고령 참여자들의 **출퇴근 상황을 자녀에게 자동으로 알림**해주는 기능을 제공한다.
- 자녀들은 부모님의 안부를 챙기고 싶지만, **바쁜 일상 속에서 출근 여부를 지속적으로 확인하기 어렵다.**
- 어르신이 **휴대폰을 소지하지 않았거나 깜빡하고 들고 오지 않은 경우에도**, 시스템을 통해 자녀가 부모님의 출·퇴근 여부를 확인할 수 있다.
- 만약 **정해진 시간에 출·퇴근 알림이 도착하지 않는다면**, 자녀는 즉시 이상 상황을 인지하고 빠르게 대응할 수 있다. 어르신의 안전 확보에 큰 도움이 된다.

# 문자 알림 기능 개발

## 기능
- Member / Guardian 저장
- CSV 업로드로 대량 등록
- CoolSMS 문자 발송 기능 테스트 가능

## 주요 기술
- Spring Boot 3.x
- JPA/Hibernate
- CoolSMS SDK
- OpenCSV
- MariaDB

# coolSMS 사용법 및 dependancies 설정

## 1.  CoolSMS 회원가입 & API 키 발급
1. [https://coolsms.co.kr](https://coolsms.co.kr) 로그인
2. **대시보드 → 프로젝트 생성**
3. 메뉴에서 **“API Key 관리”**
4. 다음 값들을 확인
    - **API Key**
    - **API Secret**
5. 발송용 전화번호 등록 (필수)
    - "발신번호 등록"에서 본인 번호 인증
    - 승인 되면 문자 API 사용 가능

## 2. 의존성 추가 (Gradle)

`dependencies {     implementation 'net.nurigo:javaSDK:4.2.3' }`

## 3. application.yml 분리 설정
- 보안을 위해 **application-sms.yml** 을 따로 만들어서 사용.
### application.yml

```
spring:   
	config:     
		import:application-sms.yml
```

### application-sms.yml

```
coolsms:   
	api-key: "발급받은 API KEY"     
	secret: "발급받은 SECRET KEY"   
	sender-number: "01012345678"   # 인증된 발신번호
```
# 📌 Attempt Project API Documentation

**Version:** v1  
**Last Update:** 2025-11-28  
**Author:** 조소영

---
## 📁 목차
1. Member API
2. Guardian API
3. Schedule API
4. Attend API
5. Excel Upload API
6. Error Response Format
---

#  1. Member API (회원)

---

## 📌 1-1. 회원 생성

**POST** `/api/v1/member`

### Request Body

```
{
  "username": "홍길동",
  "phoneNumber": "01012345678",
  "unitName": "A사업단"
}
```

### Response (201 Created)

```
{
  "id": 1,
  "username": "홍길동"
}
```

---

## 📌 1-2. 전체 회원 조회

**GET** `/api/v1/member`

### Response

```
[
  {
    "id": 1,
    "username": "홍길동",
    "phoneNumber": "01012345678",
    "unitName": "A사업단"
  }
]
```

---

## 📌 1-3. 회원 단건 조회

**GET** `/api/v1/member/{id}`

### Response

```
{
  "id": 1,
  "username": "홍길동",
  "phoneNumber": "01012345678",
  "unitName": "A사업단"
}
```

---

## 📌 1-4. 회원 수정

**PUT** `/api/v1/member/{id}`

### Request Body

```
{
  "username": "김철수",
  "phoneNumber": "01099998888"
}
```
### Response

```
{
  "id": 1,
  "username": "김철수",
  "phoneNumber": "01099998888",
  "unitName": "A사업단"
}
```
---

## 📌 1-5. 회원 삭제

**DELETE** `/api/v1/member/{id}`  
**Response:** `204 No Content`

---

# 2. Guardian API (보호자)

---

## 📌 2-1. 보호자 등록

**POST** `/api/v1/guardian`

### Request Body

```
{
  "memberId": 1,
  "name": "홍길동 아들",
  "phone": "01055556666",
  "receiveNotification": true
}
```

### Response (201 Created)

`{   "guardianId": 1 }`

---

## 📌 2-2. 특정 회원의 보호자 목록 조회

**GET** `/api/v1/guardian/member/{memberId}`

### Response

```
[
  {
    "id": 1,
    "name": "홍길동 아들",
    "phone": "01055556666",
    "receiveNotification": true
  }
]
```

---

## 📌 2-3. 보호자 단건 조회

**GET** `/api/v1/guardian/{guardianId}`

### Response

```
{
  "id": 1,
  "name": "홍길동 아들",
  "phone": "01055556666",
  "receiveNotification": true
}
```

---

#  3. Schedule API (스케줄)

---

## 📌 3-1. 스케줄 생성

**POST** `/api/v1/schedule`

### Request Body

```
{
  "attendDate": "2025-11-28T09:00:00"
}
```

### Response

```
{
  "id": 1,
  "attendDate": "2025-11-28T09:00:00"
}
```

---

## 📌 3-2. 스케줄 조회

**GET** `/api/v1/schedule/{id}`

### Response

```
{
  "id": 1,
  "attendDate": "2025-11-28T09:00:00"
}
```

---

#  4. Attend API (출퇴근)

---

## 📌 4-1. 출근 체크

**POST** `/attend/check-in`

### Query Parameters

|Key|Type|Required|Example|
|---|---|---|---|
|memberId|Long|Yes|1|
|scheduleId|Long|Yes|1|
|lat|Double|Yes|37.5512|
|lon|Double|Yes|127.1123|

### Example Request

`/attend/check-in?memberId=1&scheduleId=1&lat=37.55&lon=127.11`

### Response

`"OK"`

### 기능 설명
- Attend 저장
- GuardianRepository에서 보호자 목록 조회
- receiveNotification = true 인 보호자에게 문자 전송

---

## 📌 4-2. 퇴근 체크

**POST** `/attend/check-out`

### Example Request

`/attend/check-out?memberId=1&scheduleId=1&lat=37.55&lon=127.11`

### Response

`"OK"`

---

# 5. Excel Upload API

---

## 📌 5-1. 회원 엑셀 파싱

**POST** `/api/v1/member/member-excel`

### Form-data

|Key|Value|
|---|---|
|file|(엑셀 파일 업로드)|

### Response

```
[
  {
    "memberName": "홍길동",
    "phoneNumber": "01022223333",
    "unitName": "A사업단"
  }
]
```

---

## 📌 5-2. 파싱된 엑셀 → DB 저장

**POST** `/api/v1/member/save-members`

### Request Body

```
{
  "members": [
    {
      "memberName": "홍길동",
      "phoneNumber": "01022223333",
      "unitName": "A사업단"
    }
  ]
}
```
### Response

```
{
  "savedCount": 1,
  "message": "1명의 회원 정보가 성공적으로 저장되었습니다."
}
```

---

# ⚠️ 6. Error Response Format

### 공통 에러 형태

```
{
  "timestamp": "2025-11-27T11:39:13.477+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/v1/member"
}
```
