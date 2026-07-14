import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator_platform_interface/geolocator_platform_interface.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
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
  testWidgets('위치 권한을 거부하면 안내 메시지를 보여주고 서버를 호출하지 않는다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(permission: LocationPermission.denied);
    bool checkInCalled = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          checkInCalled = true;
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));

    await tester.tap(find.byType(ElevatedButton));
    await tester.pumpAndSettle();

    expect(find.text('위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.'), findsOneWidget);
    expect(checkInCalled, isFalse);
  });

  testWidgets('위치 확인과 체크인이 성공하면 서버 응답 메시지를 그대로 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      position: _fakePosition(),
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"success":true,"message":"출석 처리되었습니다."}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));

    await tester.tap(find.byType(ElevatedButton));
    await tester.pumpAndSettle();

    expect(find.text('출석 처리되었습니다.'), findsOneWidget);
  });

  testWidgets('위치 서비스가 꺼져 있으면 위치 확인 실패 안내를 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      throwOnPosition: true,
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}'))),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));

    await tester.tap(find.byType(ElevatedButton));
    await tester.pumpAndSettle();

    expect(find.text('위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.'), findsOneWidget);
  });
}
