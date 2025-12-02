# SMS Attendance API 명세서 (Sms_API.md)
- 개요

본 API는 노인 일자리 참여자의 출근/퇴근 시 보호자에게 자동으로 문자(SMS)를 발송하는 기능을 제공합니다.
출근(clock-in) 또는 퇴근(clock-out) 요청이 들어오면 서버에서 출석 정보를 저장하고 CoolSMS를 통해 보호자에게 알림을 보냅니다.

CoolSMS API 키는 application-sms.yml 또는 application.yml 에서 관리하며,
SmsController 내부에서 Nurigo SDK를 이용해 메시지를 발송합니다.

▶ Base URL
/sms

## 0. coolsms를 이용한 메세지 전송 설정 방법
1. `https://coolsms.co.kr/` 로 접속 및 회원가입
2. 회원가입한 전화번호로 메세지를 전송하므로 반드시 기억.
3. application-SMS.yml 파일에 API 정보 기입

```
coolsms:
  api:
    key: "" #쿨SMS에서 받은 API키
    secret: "" #쿨SMS에서 받은 SECRET키
    number: "" #핸드폰 번호
```

## 1. 출근 요청 (Clock-In)
- POST `/sms/clock-in`

노인(회원)의 출근을 기록하고 보호자에게 출근 문자 발송.

<요청 파라미터>

| 이름         | 타입     | 필수 | 설명        |
| ---------- | ------ | -- | --------- |
| memberId   | Long   | ✔  | 출근한 회원 ID |
| scheduleId | Long   | ✔  | 오늘 스케줄 ID |
| latitude   | Double | ❌  | 위치 위도(선택) |
| longitude  | Double | ❌  | 위치 경도(선택) |


<요청 예시 (POSTMAN)>

- POST
```
http://localhost:8080/sms/clock-in?memberId=1&scheduleId=1
```

<좌표 포함>

```
http://localhost:8080/sms/clock-in?memberId=1&scheduleId=1&latitude=37.123&longitude=127.123
```

- 응답(JSON)
```
{
    "memberId": 1,
    "scheduleId": 1,
    "clockInTime": "2025-12-03 09:00:15"
}
```

- 보호자 문자 발송 예시

`[출근 알림] 홍길동 님이 2025-12-03 09:00:15에 출근했습니다.`

## 2. 퇴근 요청 (Clock-Out)
POST `/sms/clock-out`

- 노인(회원)의 퇴근을 기록하고 보호자에게 퇴근 문자 발송.

<요청 파라미터>

| 이름         | 타입   | 필수 | 설명        |
| ---------- | ---- | -- | --------- |
| memberId   | Long | ✔  | 퇴근한 회원 ID |
| scheduleId | Long | ✔  | 오늘 스케줄 ID |

<요청 예시>

- POST

```
http://localhost:8080/sms/clock-out?memberId=1&scheduleId=1
```
<응답(JSON)>
```
{
    "memberId": 1,
    "scheduleId": 1,
    "clockOutTime": "2025-12-03 17:00:22"
}
```

보호자 문자 발송 예시

`[퇴근 알림] 홍길동 님이 2025-12-03 17:00:22에 퇴근했습니다.`

### [참조]
- guardianPhone의 기본적인 CRUD 기능도 모두 구현 되어 있습니다.
- 기존 README.md 파일의 API와 마찬가지로 GET, DELETE, PUT을 통해 보호자의 전화번호를 조회,삭제, 수정 가능합니다.

<예시>
- GET, DELETE, PUT
```
http://localhost:8080/api/v1/member/{id}
```
- body -> raw(json)
```
{
    "guardianPhone":"01022223333"
}
```