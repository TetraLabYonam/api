import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/attendance_history/attendance_history_screen.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/checkin/checkin_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('출석률과 날짜별 목록을 렌더링한다', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(
            fakeApiClient((options) async {
              return jsonResponse('''
          {
            "attendanceRate": 75.0,
            "records": [
              {"scheduleDate": "2026-07-01", "placeName": "중앙공원", "status": "PRESENT"},
              {"scheduleDate": "2026-07-08", "placeName": "중앙공원", "status": "ABSENT"}
            ]
          }
          ''');
            }),
          ),
        ],
        child: const MaterialApp(home: AttendanceHistoryScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('75'), findsOneWidget);
    expect(find.text('2026-07-01'), findsOneWidget);
    expect(find.text('2026-07-08'), findsOneWidget);
    expect(find.text('중앙공원'), findsNWidgets(2));
  });

  testWidgets('출석 기록이 없으면 안내 문구를 보여준다', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(
            fakeApiClient((options) async {
              return jsonResponse('{"attendanceRate":0.0,"records":[]}');
            }),
          ),
        ],
        child: const MaterialApp(home: AttendanceHistoryScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('이번 달 출석 기록이 없습니다'), findsOneWidget);
  });

  testWidgets('체크인 화면의 진입 버튼을 탭하면 출석 이력 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(
            fakeApiClient((options) async {
              if (options.path == '/api/v1/attend/today') {
                return jsonResponse('{"hasSchedule":false}');
              }
              return jsonResponse('{"attendanceRate":0.0,"records":[]}');
            }),
          ),
        ],
        child: const MaterialApp(home: CheckinScreen()),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.history));
    await tester.pumpAndSettle();

    expect(find.byType(AttendanceHistoryScreen), findsOneWidget);
  });
}
