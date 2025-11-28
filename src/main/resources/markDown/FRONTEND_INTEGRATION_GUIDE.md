# 프론트엔드 연동 가이드 (Flutter & React)

> **작성일**: 2025-11-25
> **목적**: Schedule & Attendance 시스템 프론트엔드 연동

---

## 📋 목차

1. [데이터베이스 마이그레이션](#데이터베이스-마이그레이션)
2. [Flutter 연동 가이드](#flutter-연동-가이드)
3. [React 연동 가이드](#react-연동-가이드)
4. [공통 API 명세](#공통-api-명세)

---

## 데이터베이스 마이그레이션

### 1. Flyway 설정 확인

#### build.gradle (이미 추가됨)
```gradle
dependencies {
    implementation 'org.flywaydb:flyway-core:10.15.0'
    implementation 'org.flywaydb:flyway-mysql:10.15.0'
}
```

#### application.yml (이미 설정됨)
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway 사용 시 validate로 설정
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```

### 2. 마이그레이션 실행

#### 자동 실행 (서버 시작 시)
```bash
./gradlew bootRun
```

서버가 시작되면 Flyway가 자동으로 마이그레이션을 실행합니다:
- `V2__improve_schedule_attend.sql` 스크립트 실행
- SCHEDULE, ATTEND 테이블 구조 변경
- 인덱스 생성

#### 수동 실행 (Gradle 태스크)
```bash
./gradlew flywayMigrate
```

#### 마이그레이션 상태 확인
```bash
./gradlew flywayInfo
```

#### 마이그레이션 롤백 (Clean)
```bash
./gradlew flywayClean
```

⚠️ **주의**: `flywayClean`은 모든 스키마를 삭제하므로 운영 환경에서는 절대 사용하지 마세요!

### 3. 마이그레이션 검증

#### SQL로 확인
```sql
-- Flyway 히스토리 확인
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;

-- SCHEDULE 테이블 구조 확인
DESCRIBE SCHEDULE;

-- ATTEND 테이블 구조 확인
DESCRIBE ATTEND;

-- 인덱스 확인
SHOW INDEX FROM SCHEDULE;
SHOW INDEX FROM ATTEND;
```

#### 예상 결과
```
SCHEDULE 테이블:
- id, title, description, schedule_date, start_time, end_time
- place_id, created_by, is_active
- created_at, updated_at

ATTEND 테이블:
- id, member_id, schedule_id
- status (SCHEDULED/PRESENT/ABSENT/LATE/EXCUSED)
- latitude, longitude, attended_at, note
- created_at, updated_at
```

---

## Flutter 연동 가이드 (노인 일자리 참여자용 - 어르신)

### 1. 프로젝트 구조

```
flutter_app/
├── lib/
│   ├── models/
│   │   ├── schedule.dart
│   │   ├── member.dart
│   │   └── attend.dart
│   ├── services/
│   │   ├── api_service.dart
│   │   └── schedule_service.dart
│   ├── screens/
│   │   ├── schedule_create_screen.dart
│   │   ├── schdule_list_screen.dart
│   │   └── attend_check_in_screen.dart
│   └── widgets/
│       ├── calendar_widget.dart
│       └── member_selector_widget.dart
└── pubspec.yaml
```

### 2. 의존성 추가

#### pubspec.yaml
```yaml
dependencies:
  flutter:
    sdk: flutter

  # HTTP 통신
  http: ^1.1.0
  dio: ^5.4.0

  # 상태 관리
  provider: ^6.1.1
  # 또는 riverpod: ^2.4.9

  # 캘린더
  table_calendar: ^3.0.9
  syncfusion_flutter_calendar: ^24.1.41  # 더 다양한 기능

  # 날짜/시간 처리
  intl: ^0.18.1

  # 위치 서비스
  geolocator: ^10.1.0
  permission_handler: ^11.1.0

  # UI
  flutter_datetime_picker: ^1.5.1
  multi_select_flutter: ^4.1.3
```

### 3. 모델 클래스

#### models/schedule.dart
```dart
class Schedule {
  final int? id;
  final String title;
  final String? description;
  final DateTime scheduleDate;
  final TimeOfDay? startTime;
  final TimeOfDay? endTime;
  final int placeId;
  final String? placeName;
  final bool isActive;
  final int attendeeCount;

  Schedule({
    this.id,
    required this.title,
    this.description,
    required this.scheduleDate,
    this.startTime,
    this.endTime,
    required this.placeId,
    this.placeName,
    this.isActive = true,
    this.attendeeCount = 0,
  });

  factory Schedule.fromJson(Map<String, dynamic> json) {
    return Schedule(
      id: json['scheduleId'],
      title: json['title'],
      description: json['description'],
      scheduleDate: DateTime.parse(json['scheduleDate']),
      startTime: json['startTime'] != null
          ? _parseTime(json['startTime'])
          : null,
      endTime: json['endTime'] != null
          ? _parseTime(json['endTime'])
          : null,
      placeId: json['place']?['placeId'] ?? 0,
      placeName: json['place']?['name'],
      isActive: json['isActive'] ?? true,
      attendeeCount: json['stats']?['totalAttendees'] ?? 0,
    );
  }

  static TimeOfDay _parseTime(String time) {
    final parts = time.split(':');
    return TimeOfDay(
      hour: int.parse(parts[0]),
      minute: int.parse(parts[1]),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'title': title,
      'description': description,
      'scheduleDate': scheduleDate.toIso8601String().split('T')[0],
      'startTime': startTime != null
          ? '${startTime!.hour.toString().padLeft(2, '0')}:${startTime!.minute.toString().padLeft(2, '0')}:00'
          : null,
      'endTime': endTime != null
          ? '${endTime!.hour.toString().padLeft(2, '0')}:${endTime!.minute.toString().padLeft(2, '0')}:00'
          : null,
      'placeId': placeId,
    };
  }
}
```

#### models/member.dart
```dart
class Member {
  final int id;
  final String username;
  final String phoneNumber;
  final String? unitName;

  Member({
    required this.id,
    required this.username,
    required this.phoneNumber,
    this.unitName,
  });

  factory Member.fromJson(Map<String, dynamic> json) {
    return Member(
      id: json['id'],
      username: json['username'],
      phoneNumber: json['phoneNumber'],
      unitName: json['unit']?['name'],
    );
  }
}
```

### 4. API 서비스

#### services/api_service.dart
```dart
import 'package:dio/dio.dart';

class ApiService {
  static const String baseUrl = 'http://localhost:8080/api/v1';

  final Dio _dio = Dio(
    BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: {
        'Content-Type': 'application/json',
      },
    ),
  );

  ApiService() {
    // 로깅 인터셉터 추가 (개발 환경)
    _dio.interceptors.add(LogInterceptor(
      requestBody: true,
      responseBody: true,
    ));
  }

  Dio get dio => _dio;

  // 에러 핸들링
  Future<T> handleRequest<T>(Future<Response<T>> Function() request) async {
    try {
      final response = await request();
      return response.data as T;
    } on DioException catch (e) {
      if (e.response != null) {
        throw Exception('서버 에러: ${e.response?.statusCode} - ${e.response?.data}');
      } else {
        throw Exception('네트워크 에러: ${e.message}');
      }
    }
  }
}
```

#### services/schedule_service.dart
```dart
import '../models/schedule.dart';
import 'api_service.dart';

class ScheduleService {
  final ApiService _apiService = ApiService();

  // 일정 생성
  Future<Map<String, dynamic>> createSchedules({
    required String title,
    String? description,
    required List<DateTime> dates,
    required int placeId,
    TimeOfDay? startTime,
    TimeOfDay? endTime,
    List<int>? memberIds,
    List<String>? unitNames,
    bool allMembers = false,
  }) async {
    final request = {
      'title': title,
      'description': description,
      'dates': dates.map((d) => d.toIso8601String().split('T')[0]).toList(),
      'placeId': placeId,
      'startTime': startTime != null
          ? '${startTime.hour.toString().padLeft(2, '0')}:${startTime.minute.toString().padLeft(2, '0')}:00'
          : null,
      'endTime': endTime != null
          ? '${endTime.hour.toString().padLeft(2, '0')}:${endTime.minute.toString().padLeft(2, '0')}:00'
          : null,
      'memberIds': memberIds ?? [],
      'unitNames': unitNames ?? [],
      'allMembers': allMembers,
    };

    return await _apiService.handleRequest(
      () => _apiService.dio.post('/schedule/create', data: request),
    );
  }

  // 일정 목록 조회 (날짜 범위)
  Future<List<Schedule>> getSchedulesByDateRange(
    DateTime startDate,
    DateTime endDate,
  ) async {
    final response = await _apiService.handleRequest(
      () => _apiService.dio.get(
        '/schedule/range',
        queryParameters: {
          'startDate': startDate.toIso8601String().split('T')[0],
          'endDate': endDate.toIso8601String().split('T')[0],
        },
      ),
    );

    return (response as List)
        .map((json) => Schedule.fromJson(json))
        .toList();
  }

  // 일정 상세 조회
  Future<Schedule> getScheduleDetail(int scheduleId) async {
    final response = await _apiService.handleRequest(
      () => _apiService.dio.get('/schedule/$scheduleId'),
    );

    return Schedule.fromJson(response);
  }

  // 회원별 일정 조회
  Future<List<Schedule>> getMemberSchedules(
    int memberId,
    DateTime startDate,
    DateTime endDate,
  ) async {
    final response = await _apiService.handleRequest(
      () => _apiService.dio.get(
        '/schedule/member/$memberId',
        queryParameters: {
          'startDate': startDate.toIso8601String().split('T')[0],
          'endDate': endDate.toIso8601String().split('T')[0],
        },
      ),
    );

    return (response as List)
        .map((json) => Schedule.fromJson(json))
        .toList();
  }
}
```

### 5. 일정 생성 화면

#### screens/schedule_create_screen.dart
```dart
import 'package:flutter/material.dart';
import 'package:table_calendar/table_calendar.dart';
import '../models/member.dart';
import '../services/schedule_service.dart';
import '../widgets/member_selector_widget.dart';

class ScheduleCreateScreen extends StatefulWidget {
  const ScheduleCreateScreen({Key? key}) : super(key: key);

  @override
  State<ScheduleCreateScreen> createState() => _ScheduleCreateScreenState();
}

class _ScheduleCreateScreenState extends State<ScheduleCreateScreen> {
  final _formKey = GlobalKey<FormState>();
  final _scheduleService = ScheduleService();

  // 폼 필드
  String _title = '';
  String _description = '';
  List<DateTime> _selectedDates = [];
  TimeOfDay? _startTime;
  TimeOfDay? _endTime;
  int? _selectedPlaceId;
  List<int> _selectedMemberIds = [];
  bool _allMembers = false;

  // 캘린더
  DateTime _focusedDay = DateTime.now();
  CalendarFormat _calendarFormat = CalendarFormat.month;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('일정 생성'),
        actions: [
          IconButton(
            icon: const Icon(Icons.check),
            onPressed: _submitForm,
          ),
        ],
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // 일정 제목
            TextFormField(
              decoration: const InputDecoration(
                labelText: '일정 제목 *',
                border: OutlineInputBorder(),
              ),
              validator: (value) {
                if (value == null || value.isEmpty) {
                  return '일정 제목을 입력하세요';
                }
                return null;
              },
              onSaved: (value) => _title = value!,
            ),
            const SizedBox(height: 16),

            // 일정 설명
            TextFormField(
              decoration: const InputDecoration(
                labelText: '일정 설명',
                border: OutlineInputBorder(),
              ),
              maxLines: 3,
              onSaved: (value) => _description = value ?? '',
            ),
            const SizedBox(height: 16),

            // 캘린더 (여러 날짜 선택)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(8.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '날짜 선택 *',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    TableCalendar(
                      firstDay: DateTime.now(),
                      lastDay: DateTime.now().add(const Duration(days: 365)),
                      focusedDay: _focusedDay,
                      calendarFormat: _calendarFormat,
                      selectedDayPredicate: (day) {
                        return _selectedDates.any((d) =>
                            isSameDay(d, day));
                      },
                      onDaySelected: (selectedDay, focusedDay) {
                        setState(() {
                          _focusedDay = focusedDay;
                          if (_selectedDates.any((d) =>
                              isSameDay(d, selectedDay))) {
                            _selectedDates.removeWhere((d) =>
                                isSameDay(d, selectedDay));
                          } else {
                            _selectedDates.add(selectedDay);
                          }
                        });
                      },
                      onFormatChanged: (format) {
                        setState(() {
                          _calendarFormat = format;
                        });
                      },
                    ),
                    if (_selectedDates.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Wrap(
                          spacing: 8,
                          children: _selectedDates.map((date) {
                            return Chip(
                              label: Text(
                                '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}',
                              ),
                              onDeleted: () {
                                setState(() {
                                  _selectedDates.remove(date);
                                });
                              },
                            );
                          }).toList(),
                        ),
                      ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // 시작 시간
            ListTile(
              title: const Text('시작 시간'),
              subtitle: Text(
                _startTime != null
                    ? _startTime!.format(context)
                    : '선택 안 함',
              ),
              trailing: const Icon(Icons.access_time),
              onTap: () async {
                final time = await showTimePicker(
                  context: context,
                  initialTime: _startTime ?? TimeOfDay.now(),
                );
                if (time != null) {
                  setState(() {
                    _startTime = time;
                  });
                }
              },
            ),

            // 종료 시간
            ListTile(
              title: const Text('종료 시간'),
              subtitle: Text(
                _endTime != null
                    ? _endTime!.format(context)
                    : '선택 안 함',
              ),
              trailing: const Icon(Icons.access_time),
              onTap: () async {
                final time = await showTimePicker(
                  context: context,
                  initialTime: _endTime ?? TimeOfDay.now(),
                );
                if (time != null) {
                  setState(() {
                    _endTime = time;
                  });
                }
              },
            ),
            const SizedBox(height: 16),

            // 장소 선택 (드롭다운)
            DropdownButtonFormField<int>(
              decoration: const InputDecoration(
                labelText: '장소 *',
                border: OutlineInputBorder(),
              ),
              value: _selectedPlaceId,
              items: [
                // TODO: 실제 장소 목록을 API에서 가져와 표시
                const DropdownMenuItem(value: 1, child: Text('중앙 교육센터')),
                const DropdownMenuItem(value: 2, child: Text('동부 작업장')),
                const DropdownMenuItem(value: 3, child: Text('서부 작업장')),
              ],
              onChanged: (value) {
                setState(() {
                  _selectedPlaceId = value;
                });
              },
              validator: (value) {
                if (value == null) {
                  return '장소를 선택하세요';
                }
                return null;
              },
            ),
            const SizedBox(height: 16),

            // 참석자 선택
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '참석자 선택 *',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    SwitchListTile(
                      title: const Text('전체 회원'),
                      value: _allMembers,
                      onChanged: (value) {
                        setState(() {
                          _allMembers = value;
                          if (value) {
                            _selectedMemberIds.clear();
                          }
                        });
                      },
                    ),
                    if (!_allMembers)
                      ElevatedButton.icon(
                        icon: const Icon(Icons.person_add),
                        label: Text(
                          _selectedMemberIds.isEmpty
                              ? '회원 선택'
                              : '${_selectedMemberIds.length}명 선택됨',
                        ),
                        onPressed: () async {
                          final result = await Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (context) => MemberSelectorWidget(
                                selectedIds: _selectedMemberIds,
                              ),
                            ),
                          );
                          if (result != null) {
                            setState(() {
                              _selectedMemberIds = result;
                            });
                          }
                        },
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submitForm() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    if (_selectedDates.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('최소 하나의 날짜를 선택하세요')),
      );
      return;
    }

    if (!_allMembers && _selectedMemberIds.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('참석자를 선택하세요')),
      );
      return;
    }

    _formKey.currentState!.save();

    try {
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (context) => const Center(
          child: CircularProgressIndicator(),
        ),
      );

      final result = await _scheduleService.createSchedules(
        title: _title,
        description: _description.isNotEmpty ? _description : null,
        dates: _selectedDates,
        placeId: _selectedPlaceId!,
        startTime: _startTime,
        endTime: _endTime,
        memberIds: _allMembers ? null : _selectedMemberIds,
        allMembers: _allMembers,
      );

      Navigator.of(context).pop(); // 로딩 다이얼로그 닫기

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(result['message'] ?? '일정이 생성되었습니다'),
          backgroundColor: Colors.green,
        ),
      );

      Navigator.of(context).pop(); // 화면 닫기
    } catch (e) {
      Navigator.of(context).pop(); // 로딩 다이얼로그 닫기

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('일정 생성 실패: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }
}
```

### 6. 출석 체크인 화면

#### screens/attend_check_in_screen.dart
```dart
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';

class AttendCheckInScreen extends StatefulWidget {
  final int scheduleId;
  final int memberId;

  const AttendCheckInScreen({
    Key? key,
    required this.scheduleId,
    required this.memberId,
  }) : super(key: key);

  @override
  State<AttendCheckInScreen> createState() => _AttendCheckInScreenState();
}

class _AttendCheckInScreenState extends State<AttendCheckInScreen> {
  bool _isLoading = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('출석 체크인'),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.location_on,
              size: 100,
              color: Colors.blue,
            ),
            const SizedBox(height: 24),
            const Text(
              '출석하기',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            const Text(
              '현재 위치를 확인하여 출석 처리합니다',
              style: TextStyle(color: Colors.grey),
            ),
            const SizedBox(height: 48),
            ElevatedButton.icon(
              icon: _isLoading
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        color: Colors.white,
                        strokeWidth: 2,
                      ),
                    )
                  : const Icon(Icons.check),
              label: Text(_isLoading ? '처리 중...' : '출석하기'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 48,
                  vertical: 16,
                ),
                textStyle: const TextStyle(fontSize: 18),
              ),
              onPressed: _isLoading ? null : _checkIn,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _checkIn() async {
    setState(() {
      _isLoading = true;
    });

    try {
      // 1. 위치 권한 확인
      final permission = await Permission.location.request();
      if (!permission.isGranted) {
        throw Exception('위치 권한이 필요합니다');
      }

      // 2. 현재 위치 가져오기
      final position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );

      // 3. 출석 체크인 API 호출
      final response = await _apiService.dio.post(
        '/attend/check-in',
        data: {
          'scheduleId': widget.scheduleId,
          'memberId': widget.memberId,
          'latitude': position.latitude,
          'longitude': position.longitude,
        },
      );

      final result = response.data;

      if (result['success']) {
        _showSuccessDialog(result);
      } else {
        throw Exception(result['message']);
      }
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('출석 실패: $e'),
          backgroundColor: Colors.red,
        ),
      );
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _showSuccessDialog(Map<String, dynamic> result) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Row(
          children: [
            Icon(
              result['isLate'] ? Icons.warning : Icons.check_circle,
              color: result['isLate'] ? Colors.orange : Colors.green,
            ),
            const SizedBox(width: 8),
            Text(result['isLate'] ? '지각' : '출석 완료'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(result['message']),
            const SizedBox(height: 8),
            Text(
              result['locationInfo'],
              style: const TextStyle(
                fontSize: 12,
                color: Colors.grey,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(context).pop(); // 다이얼로그 닫기
              Navigator.of(context).pop(); // 화면 닫기
            },
            child: const Text('확인'),
          ),
        ],
      ),
    );
  }

  final _apiService = ApiService();
}
```

---

## React 연동 가이드 (노인 일자리 담당자용 - 개발자/사무직 노동자)

### 1. 프로젝트 구조

```
react-app/
├── src/
│   ├── api/
│   │   ├── scheduleApi.ts
│   │   └── attendApi.ts
│   ├── types/
│   │   ├── schedule.ts
│   │   └── member.ts
│   ├── components/
│   │   ├── ScheduleCalendar.tsx
│   │   ├── ScheduleCreateForm.tsx
│   │   ├── MemberSelector.tsx
│   │   └── AttendCheckIn.tsx
│   ├── pages/
│   │   ├── SchedulePage.tsx
│   │   └── AttendancePage.tsx
│   └── App.tsx
└── package.json
```

### 2. 의존성 설치

```bash
npm install axios react-calendar react-big-calendar date-fns
npm install @mui/material @mui/icons-material @emotion/react @emotion/styled
npm install react-multi-select-component
npm install @types/react-calendar @types/react-big-calendar --save-dev
```

### 3. 타입 정의

#### types/schedule.ts
```typescript
export interface Schedule {
  scheduleId: number;
  title: string;
  description?: string;
  scheduleDate: string; // YYYY-MM-DD
  startTime?: string;   // HH:mm:ss
  endTime?: string;
  place: {
    placeId: number;
    name: string;
    address: string;
    latitude: number;
    longitude: number;
  };
  stats: {
    totalAttendees: number;
    presentCount: number;
    absentCount: number;
    lateCount: number;
    scheduledCount: number;
    attendanceRate: number;
  };
  attendees: Attendee[];
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Attendee {
  memberId: number;
  memberName: string;
  phoneNumber: string;
  unitName?: string;
  status: 'SCHEDULED' | 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';
  attendedAt?: string;
  note?: string;
}

export interface ScheduleCreateRequest {
  title: string;
  description?: string;
  dates: string[]; // YYYY-MM-DD[]
  placeId: number;
  startTime?: string;
  endTime?: string;
  memberIds?: number[];
  unitNames?: string[];
  allMembers?: boolean;
}

export interface ScheduleCreateResponse {
  schedules: {
    scheduleId: number;
    title: string;
    scheduleDate: string;
    startTime?: string;
    endTime?: string;
    placeName: string;
    attendeeCount: number;
    createdAt: string;
  }[];
  totalSchedulesCreated: number;
  totalAttendeesPerSchedule: number;
  message: string;
}
```

### 4. API 서비스

#### api/scheduleApi.ts
```typescript
import axios from 'axios';
import type { Schedule, ScheduleCreateRequest, ScheduleCreateResponse } from '../types/schedule';

const API_BASE_URL = 'http://localhost:8080/api/v1';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// 에러 핸들링 인터셉터
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      console.error('API Error:', error.response.data);
      throw new Error(error.response.data.message || '서버 오류가 발생했습니다');
    } else if (error.request) {
      console.error('Network Error:', error.request);
      throw new Error('네트워크 오류가 발생했습니다');
    } else {
      console.error('Error:', error.message);
      throw error;
    }
  }
);

export const scheduleApi = {
  // 일정 생성
  async createSchedules(request: ScheduleCreateRequest): Promise<ScheduleCreateResponse> {
    const response = await api.post<ScheduleCreateResponse>('/schedule/create', request);
    return response.data;
  },

  // 일정 상세 조회
  async getScheduleDetail(scheduleId: number): Promise<Schedule> {
    const response = await api.get<Schedule>(`/schedule/${scheduleId}`);
    return response.data;
  },

  // 날짜 범위로 일정 조회
  async getSchedulesByDateRange(startDate: string, endDate: string): Promise<Schedule[]> {
    const response = await api.get<Schedule[]>('/schedule/range', {
      params: { startDate, endDate },
    });
    return response.data;
  },

  // 특정 날짜의 일정 조회
  async getSchedulesByDate(date: string): Promise<Schedule[]> {
    const response = await api.get<Schedule[]>('/schedule/date', {
      params: { date },
    });
    return response.data;
  },

  // 회원별 일정 조회
  async getMemberSchedules(
    memberId: number,
    startDate: string,
    endDate: string
  ): Promise<Schedule[]> {
    const response = await api.get<Schedule[]>(`/schedule/member/${memberId}`, {
      params: { startDate, endDate },
    });
    return response.data;
  },

  // 일정 활성화/비활성화
  async toggleScheduleActive(scheduleId: number, activate: boolean): Promise<void> {
    const action = activate ? 'activate' : 'deactivate';
    await api.put(`/schedule/${scheduleId}/${action}`);
  },
};

export default scheduleApi;
```

### 5. 캘린더 컴포넌트

#### components/ScheduleCalendar.tsx
```typescript
import React, { useState, useEffect } from 'react';
import { Calendar, momentLocalizer, View } from 'react-big-calendar';
import moment from 'moment';
import 'react-big-calendar/lib/css/react-big-calendar.css';
import { scheduleApi } from '../api/scheduleApi';
import type { Schedule } from '../types/schedule';

const localizer = momentLocalizer(moment);

interface ScheduleCalendarProps {
  onSelectDate?: (date: Date) => void;
  onSelectSchedule?: (schedule: Schedule) => void;
}

const ScheduleCalendar: React.FC<ScheduleCalendarProps> = ({
  onSelectDate,
  onSelectSchedule,
}) => {
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [currentView, setCurrentView] = useState<View>('month');
  const [currentDate, setCurrentDate] = useState(new Date());
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadSchedules();
  }, [currentDate, currentView]);

  const loadSchedules = async () => {
    setLoading(true);
    try {
      const startDate = moment(currentDate).startOf(currentView).format('YYYY-MM-DD');
      const endDate = moment(currentDate).endOf(currentView).format('YYYY-MM-DD');

      const data = await scheduleApi.getSchedulesByDateRange(startDate, endDate);
      setSchedules(data);
    } catch (error) {
      console.error('일정 조회 실패:', error);
    } finally {
      setLoading(false);
    }
  };

  // Schedule을 Calendar 이벤트로 변환
  const events = schedules.map((schedule) => ({
    id: schedule.scheduleId,
    title: `${schedule.title} (${schedule.stats.totalAttendees}명)`,
    start: new Date(`${schedule.scheduleDate}T${schedule.startTime || '00:00:00'}`),
    end: new Date(`${schedule.scheduleDate}T${schedule.endTime || '23:59:59'}`),
    resource: schedule,
  }));

  const handleSelectEvent = (event: any) => {
    if (onSelectSchedule) {
      onSelectSchedule(event.resource);
    }
  };

  const handleSelectSlot = (slotInfo: any) => {
    if (onSelectDate) {
      onSelectDate(slotInfo.start);
    }
  };

  const handleNavigate = (date: Date) => {
    setCurrentDate(date);
  };

  const handleViewChange = (view: View) => {
    setCurrentView(view);
  };

  // 이벤트 스타일 커스터마이징
  const eventStyleGetter = (event: any) => {
    const schedule = event.resource as Schedule;
    const rate = schedule.stats.attendanceRate;

    let backgroundColor = '#3174ad'; // 기본 색상

    if (rate >= 90) {
      backgroundColor = '#28a745'; // 녹색 (높은 출석률)
    } else if (rate >= 70) {
      backgroundColor = '#ffc107'; // 노란색 (중간 출석률)
    } else if (rate < 70 && schedule.stats.presentCount > 0) {
      backgroundColor = '#dc3545'; // 빨간색 (낮은 출석률)
    }

    return {
      style: {
        backgroundColor,
        borderRadius: '5px',
        opacity: 0.8,
        color: 'white',
        border: '0',
        display: 'block',
      },
    };
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '50px' }}>
        <div>로딩 중...</div>
      </div>
    );
  }

  return (
    <div style={{ height: '700px' }}>
      <Calendar
        localizer={localizer}
        events={events}
        startAccessor="start"
        endAccessor="end"
        views={['month', 'week', 'day']}
        view={currentView}
        onView={handleViewChange}
        date={currentDate}
        onNavigate={handleNavigate}
        onSelectEvent={handleSelectEvent}
        onSelectSlot={handleSelectSlot}
        selectable
        eventPropGetter={eventStyleGetter}
        messages={{
          next: '다음',
          previous: '이전',
          today: '오늘',
          month: '월',
          week: '주',
          day: '일',
        }}
      />
    </div>
  );
};

export default ScheduleCalendar;
```

### 6. 일정 생성 폼

#### components/ScheduleCreateForm.tsx
```typescript
import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Chip,
  Box,
  Switch,
  FormControlLabel,
  Grid,
} from '@mui/material';
import Calendar from 'react-calendar';
import 'react-calendar/dist/Calendar.css';
import { scheduleApi } from '../api/scheduleApi';
import MemberSelector from './MemberSelector';
import type { ScheduleCreateRequest } from '../types/schedule';

interface ScheduleCreateFormProps {
  open: boolean;
  onClose: () => void;
  onSuccess?: () => void;
}

const ScheduleCreateForm: React.FC<ScheduleCreateFormProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [selectedDates, setSelectedDates] = useState<Date[]>([]);
  const [placeId, setPlaceId] = useState<number>(0);
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [allMembers, setAllMembers] = useState(false);
  const [selectedMemberIds, setSelectedMemberIds] = useState<number[]>([]);
  const [showMemberSelector, setShowMemberSelector] = useState(false);
  const [loading, setLoading] = useState(false);

  // TODO: 실제 장소 목록을 API에서 가져오기
  const places = [
    { id: 1, name: '중앙 교육센터' },
    { id: 2, name: '동부 작업장' },
    { id: 3, name: '서부 작업장' },
  ];

  const handleDateClick = (date: Date) => {
    const exists = selectedDates.some(
      (d) => d.toDateString() === date.toDateString()
    );

    if (exists) {
      setSelectedDates(selectedDates.filter(
        (d) => d.toDateString() !== date.toDateString()
      ));
    } else {
      setSelectedDates([...selectedDates, date]);
    }
  };

  const handleSubmit = async () => {
    if (!title.trim()) {
      alert('일정 제목을 입력하세요');
      return;
    }

    if (selectedDates.length === 0) {
      alert('최소 하나의 날짜를 선택하세요');
      return;
    }

    if (placeId === 0) {
      alert('장소를 선택하세요');
      return;
    }

    if (!allMembers && selectedMemberIds.length === 0) {
      alert('참석자를 선택하세요');
      return;
    }

    setLoading(true);

    try {
      const request: ScheduleCreateRequest = {
        title,
        description: description || undefined,
        dates: selectedDates
          .map((d) => d.toISOString().split('T')[0])
          .sort(),
        placeId,
        startTime: startTime || undefined,
        endTime: endTime || undefined,
        memberIds: allMembers ? undefined : selectedMemberIds,
        allMembers,
      };

      const response = await scheduleApi.createSchedules(request);

      alert(response.message);
      onClose();
      if (onSuccess) {
        onSuccess();
      }
    } catch (error: any) {
      alert(`일정 생성 실패: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
        <DialogTitle>일정 생성</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <TextField
              fullWidth
              label="일정 제목"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              margin="normal"
            />

            <TextField
              fullWidth
              label="일정 설명"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              multiline
              rows={3}
              margin="normal"
            />

            <Box sx={{ mt: 2, mb: 2 }}>
              <InputLabel>날짜 선택 *</InputLabel>
              <Calendar
                onChange={(date) => handleDateClick(date as Date)}
                minDate={new Date()}
                tileClassName={({ date }) =>
                  selectedDates.some(
                    (d) => d.toDateString() === date.toDateString()
                  )
                    ? 'selected-date'
                    : ''
                }
              />
              <Box sx={{ mt: 1 }}>
                {selectedDates.map((date, index) => (
                  <Chip
                    key={index}
                    label={date.toLocaleDateString('ko-KR')}
                    onDelete={() => handleDateClick(date)}
                    sx={{ mr: 1, mb: 1 }}
                  />
                ))}
              </Box>
            </Box>

            <Grid container spacing={2}>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  label="시작 시간"
                  type="time"
                  value={startTime}
                  onChange={(e) => setStartTime(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  inputProps={{ step: 300 }}
                />
              </Grid>
              <Grid item xs={6}>
                <TextField
                  fullWidth
                  label="종료 시간"
                  type="time"
                  value={endTime}
                  onChange={(e) => setEndTime(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  inputProps={{ step: 300 }}
                />
              </Grid>
            </Grid>

            <FormControl fullWidth margin="normal" required>
              <InputLabel>장소</InputLabel>
              <Select
                value={placeId}
                onChange={(e) => setPlaceId(e.target.value as number)}
              >
                <MenuItem value={0}>선택하세요</MenuItem>
                {places.map((place) => (
                  <MenuItem key={place.id} value={place.id}>
                    {place.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Box sx={{ mt: 2 }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={allMembers}
                    onChange={(e) => {
                      setAllMembers(e.target.checked);
                      if (e.target.checked) {
                        setSelectedMemberIds([]);
                      }
                    }}
                  />
                }
                label="전체 회원"
              />

              {!allMembers && (
                <Button
                  variant="outlined"
                  onClick={() => setShowMemberSelector(true)}
                  sx={{ ml: 2 }}
                >
                  회원 선택 ({selectedMemberIds.length}명)
                </Button>
              )}
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>취소</Button>
          <Button
            onClick={handleSubmit}
            variant="contained"
            disabled={loading}
          >
            {loading ? '생성 중...' : '생성'}
          </Button>
        </DialogActions>
      </Dialog>

      <MemberSelector
        open={showMemberSelector}
        selectedIds={selectedMemberIds}
        onClose={() => setShowMemberSelector(false)}
        onSelect={(ids) => {
          setSelectedMemberIds(ids);
          setShowMemberSelector(false);
        }}
      />

      <style>{`
        .selected-date {
          background-color: #1976d2 !important;
          color: white !important;
        }
      `}</style>
    </>
  );
};

export default ScheduleCreateForm;
```

### 7. 회원 선택 컴포넌트

#### components/MemberSelector.tsx
```typescript
import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  List,
  ListItem,
  ListItemText,
  Checkbox,
  TextField,
  Box,
} from '@mui/material';
import axios from 'axios';

interface Member {
  id: number;
  username: string;
  phoneNumber: string;
  unit?: {
    name: string;
  };
}

interface MemberSelectorProps {
  open: boolean;
  selectedIds: number[];
  onClose: () => void;
  onSelect: (ids: number[]) => void;
}

const MemberSelector: React.FC<MemberSelectorProps> = ({
  open,
  selectedIds,
  onClose,
  onSelect,
}) => {
  const [members, setMembers] = useState<Member[]>([]);
  const [filteredMembers, setFilteredMembers] = useState<Member[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [tempSelectedIds, setTempSelectedIds] = useState<number[]>(selectedIds);

  useEffect(() => {
    if (open) {
      loadMembers();
      setTempSelectedIds(selectedIds);
    }
  }, [open, selectedIds]);

  useEffect(() => {
    if (searchTerm) {
      setFilteredMembers(
        members.filter(
          (m) =>
            m.username.includes(searchTerm) ||
            m.phoneNumber.includes(searchTerm) ||
            m.unit?.name.includes(searchTerm)
        )
      );
    } else {
      setFilteredMembers(members);
    }
  }, [searchTerm, members]);

  const loadMembers = async () => {
    try {
      // TODO: 실제 API 엔드포인트로 교체
      const response = await axios.get('http://localhost:8080/api/v1/member');
      setMembers(response.data);
      setFilteredMembers(response.data);
    } catch (error) {
      console.error('회원 조회 실패:', error);
    }
  };

  const handleToggle = (memberId: number) => {
    if (tempSelectedIds.includes(memberId)) {
      setTempSelectedIds(tempSelectedIds.filter((id) => id !== memberId));
    } else {
      setTempSelectedIds([...tempSelectedIds, memberId]);
    }
  };

  const handleSelectAll = () => {
    if (tempSelectedIds.length === filteredMembers.length) {
      setTempSelectedIds([]);
    } else {
      setTempSelectedIds(filteredMembers.map((m) => m.id));
    }
  };

  const handleConfirm = () => {
    onSelect(tempSelectedIds);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        회원 선택 ({tempSelectedIds.length}명)
      </DialogTitle>
      <DialogContent>
        <Box sx={{ mb: 2 }}>
          <TextField
            fullWidth
            placeholder="이름, 전화번호, 사업단으로 검색"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          <Button
            variant="outlined"
            size="small"
            onClick={handleSelectAll}
            sx={{ mt: 1 }}
          >
            {tempSelectedIds.length === filteredMembers.length
              ? '전체 해제'
              : '전체 선택'}
          </Button>
        </Box>
        <List sx={{ maxHeight: '400px', overflow: 'auto' }}>
          {filteredMembers.map((member) => (
            <ListItem
              key={member.id}
              dense
              button
              onClick={() => handleToggle(member.id)}
            >
              <Checkbox
                edge="start"
                checked={tempSelectedIds.includes(member.id)}
                tabIndex={-1}
                disableRipple
              />
              <ListItemText
                primary={member.username}
                secondary={`${member.phoneNumber} | ${member.unit?.name || '미지정'}`}
              />
            </ListItem>
          ))}
        </List>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>취소</Button>
        <Button onClick={handleConfirm} variant="contained">
          확인
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default MemberSelector;
```

### 8. 메인 페이지

#### pages/SchedulePage.tsx
```typescript
import React, { useState } from 'react';
import {
  Container,
  Box,
  Button,
  Typography,
  Paper,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ScheduleCalendar from '../components/ScheduleCalendar';
import ScheduleCreateForm from '../components/ScheduleCreateForm';
import type { Schedule } from '../types/schedule';

const SchedulePage: React.FC = () => {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedSchedule, setSelectedSchedule] = useState<Schedule | null>(null);

  const handleCreateSuccess = () => {
    setShowCreateForm(false);
    // 캘린더 새로고침은 ScheduleCalendar 컴포넌트에서 자동으로 처리됨
  };

  const handleSelectSchedule = (schedule: Schedule) => {
    setSelectedSchedule(schedule);
    // TODO: 일정 상세 정보 다이얼로그 표시
    console.log('선택된 일정:', schedule);
  };

  return (
    <Container maxWidth="xl">
      <Box sx={{ py: 4 }}>
        <Box
          sx={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            mb: 3,
          }}
        >
          <Typography variant="h4" component="h1">
            일정 관리
          </Typography>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setShowCreateForm(true)}
          >
            일정 생성
          </Button>
        </Box>

        <Paper sx={{ p: 2 }}>
          <ScheduleCalendar
            onSelectSchedule={handleSelectSchedule}
          />
        </Paper>
      </Box>

      <ScheduleCreateForm
        open={showCreateForm}
        onClose={() => setShowCreateForm(false)}
        onSuccess={handleCreateSuccess}
      />
    </Container>
  );
};

export default SchedulePage;
```

---

## 공통 API 명세

### 일정 생성 API

**Endpoint**: `POST /api/v1/schedule/create`

**Request Body**:
```json
{
  "title": "현장 안전 교육",
  "description": "2025년 1분기 안전 교육",
  "dates": ["2025-01-02", "2025-01-03"],
  "placeId": 1,
  "startTime": "09:00:00",
  "endTime": "12:00:00",
  "memberIds": [1, 2, 3],
  "allMembers": false
}
```

**Response**:
```json
{
  "schedules": [
    {
      "scheduleId": 101,
      "title": "현장 안전 교육",
      "scheduleDate": "2025-01-02",
      "startTime": "09:00:00",
      "endTime": "12:00:00",
      "placeName": "중앙 교육센터",
      "attendeeCount": 45,
      "createdAt": "2025-11-25 10:30:00"
    }
  ],
  "totalSchedulesCreated": 2,
  "totalAttendeesPerSchedule": 45,
  "message": "2개의 일정이 성공적으로 생성되었습니다."
}
```

### 출석 체크인 API

**Endpoint**: `POST /api/v1/attend/check-in`

**Request Body**:
```json
{
  "scheduleId": 101,
  "memberId": 5,
  "latitude": 37.5665,
  "longitude": 126.9780
}
```

**Response**:
```json
{
  "attendId": 1234,
  "status": "PRESENT",
  "attendedAt": "2025-01-02T09:15:30",
  "message": "출석 처리되었습니다.",
  "isLate": false,
  "locationInfo": "위치 확인 완료 (거리: 45.3m)",
  "distance": 45.3,
  "success": true
}
```

---

## 테스트 시나리오

### 1. 일정 생성 테스트

1. React/Flutter 앱 실행
2. 일정 생성 화면으로 이동
3. 캘린더에서 여러 날짜 선택
4. 제목, 장소, 시작/종료 시간 입력
5. 참석자 선택 (전체 또는 특정 회원)
6. 생성 버튼 클릭
7. 성공 메시지 확인
8. 캘린더에 일정이 표시되는지 확인

### 2. 출석 체크인 테스트

1. 일정 상세 화면으로 이동
2. 출석 체크인 버튼 클릭
3. 위치 권한 허용
4. 현재 위치가 일정 장소 100m 이내인지 확인
5. 출석 완료 메시지 확인
6. 일정 상세 화면에서 출석 상태 확인

---

## 🔒 보안 고려사항

1. **API 인증/인가**
   - 현재는 인증 없음
   - 실제 배포 시 JWT 또는 OAuth2 구현 필요

2. **CORS 설정**
   - React: `http://localhost:5173`
   - Flutter: 모바일 앱은 CORS 영향 없음

3. **환경 변수**
   - API URL을 환경 변수로 관리
   - React: `.env.local`
   - Flutter: `--dart-define` 사용

---

## 📱 배포 가이드

### React 배포

```bash
# 프로덕션 빌드
npm run build

# 정적 파일 서빙
npx serve -s build
```

### Flutter 배포

```bash
# Android APK
flutter build apk --release

# iOS IPA
flutter build ios --release
```

---

🤖 Document Generated: 2025-11-25
