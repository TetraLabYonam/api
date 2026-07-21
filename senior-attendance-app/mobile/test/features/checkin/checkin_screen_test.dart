import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator_platform_interface/geolocator_platform_interface.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/login_screen.dart';
import 'package:senior_job_attendance/features/checkin/checkin_screen.dart';

import '../../support/fake_api_client.dart';

class _FakeGeolocatorPlatform extends GeolocatorPlatform {
  final LocationPermission permission;
  final Position? position;
  final bool throwOnPosition;

  _FakeGeolocatorPlatform({required this.permission, this.position, this.throwOnPosition = false});

  @override
  Future<LocationPermission> requestPermission() async => permission;

  @override
  Future<Position> getCurrentPosition({LocationSettings? locationSettings}) async {
    if (throwOnPosition) {
      throw const LocationServiceDisabledException();
    }
    return position!;
  }
}

Position _fakePosition() {
  return Position(
    latitude: 35.3,
    longitude: 129.0,
    timestamp: DateTime.now(),
    accuracy: 1.0,
    altitude: 0.0,
    altitudeAccuracy: 1.0,
    heading: 0.0,
    headingAccuracy: 1.0,
    speed: 0.0,
    speedAccuracy: 1.0,
  );
}

void main() {
  testWidgets('오늘 일정이 없으면 안내 문구를 보여주고 네/아니오 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"hasSchedule":false}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    expect(find.text('오늘은 예정된 출석이 없습니다'), findsOneWidget);
    final yesButton = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '네'));
    final noButton = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '아니오'));
    expect(yesButton.onPressed, isNull);
    expect(noButton.onPressed, isNull);
  });

  testWidgets('위치 권한을 거부하면 안내 메시지를 보여주고 서버를 호출하지 않는다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(permission: LocationPermission.denied);
    bool checkInCalled = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/check-in') {
            checkInCalled = true;
          }
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.'), findsOneWidget);
    expect(checkInCalled, isFalse);
  });

  testWidgets('위치 확인과 체크인이 성공하면 결과 화면에 서버 응답 메시지를 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      position: _fakePosition(),
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          return jsonResponse('{"success":true,"message":"출석 처리되었습니다."}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('출석 처리되었습니다.'), findsOneWidget);
    expect(find.widgetWithText(OutlinedButton, '확인'), findsOneWidget);
  });

  testWidgets('아니오를 누르면 결석 처리 API를 호출하고 결과 화면에 메시지를 보여준다', (tester) async {
    bool declineCalled = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          if (options.path == '/api/v1/attend/decline') {
            declineCalled = true;
            return jsonResponse('{"success":true,"message":"결석 처리되었습니다."}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '아니오'));
    await tester.pumpAndSettle();

    expect(declineCalled, isTrue);
    expect(find.text('결석 처리되었습니다.'), findsOneWidget);
    expect(find.widgetWithText(OutlinedButton, '확인'), findsOneWidget);
  });

  testWidgets('결석 처리 API가 실패 응답을 반환하면 결과 화면에 서버 메시지를 보여준다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          if (options.path == '/api/v1/attend/decline') {
            return jsonResponse('{"message":"서버 오류"}', statusCode: 500);
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '아니오'));
    await tester.pumpAndSettle();

    // CheckinRepository.decline() already catches DioException internally and
    // returns a CheckinResult(success: false, message: <server message>) — it
    // never rethrows. So a 500 here surfaces through the same `_result` success
    // screen as a normal decline, showing the server's message, not through
    // `_errorMessage`. Only a non-Dio exception (not exercised by this fake
    // adapter setup) would reach `_declineCheckIn()`'s own catch block.
    expect(find.text('서버 오류'), findsOneWidget);
    expect(find.widgetWithText(OutlinedButton, '확인'), findsOneWidget);
  });

  testWidgets('위치 서비스가 꺼져 있으면 위치 확인 실패 안내를 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      throwOnPosition: true,
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.'), findsOneWidget);
  });

  testWidgets('로그아웃 버튼을 탭하면 확인 다이얼로그가 뜬다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"hasSchedule":false}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.logout));
    await tester.pumpAndSettle();

    expect(find.text('로그아웃하시겠어요?'), findsOneWidget);
    expect(find.widgetWithText(TextButton, '취소'), findsOneWidget);
    expect(find.widgetWithText(TextButton, '로그아웃'), findsOneWidget);
  });

  testWidgets('확인 다이얼로그에서 취소를 누르면 아무 일도 일어나지 않는다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"hasSchedule":false}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.logout));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(TextButton, '취소'));
    await tester.pumpAndSettle();

    expect(find.text('로그아웃하시겠어요?'), findsNothing);
    expect(find.byType(CheckinScreen), findsOneWidget);
    expect(find.byType(LoginScreen), findsNothing);
  });

  testWidgets('확인 다이얼로그에서 로그아웃을 누르면 로그인 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"hasSchedule":false}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.byIcon(Icons.logout));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(TextButton, '로그아웃'));
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.byType(CheckinScreen), findsNothing);
  });
}
