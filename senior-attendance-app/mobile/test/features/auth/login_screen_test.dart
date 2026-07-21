import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/login_screen.dart';
import 'package:senior_job_attendance/features/consent/consent_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('직번과 전화번호를 입력하고 로그인을 누르면 위치동의 화면으로 이동한다', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(ProviderScope(
      overrides: [apiClientProvider.overrideWithValue(fakeApiClient((options) async {
        return jsonResponse('{"accessToken":"tok","memberId":1}');
      }))],
      child: const MaterialApp(home: LoginScreen()),
    ));
    await tester.enterText(find.byKey(const Key('employeeIdField')), '1001');
    await tester.enterText(find.byKey(const Key('phoneNumberField')), '01012345678');
    await tester.tap(find.widgetWithText(ElevatedButton, '로그인'));
    await tester.pumpAndSettle();
    expect(find.byType(ConsentScreen), findsOneWidget);
  });

  testWidgets('직번 또는 전화번호가 올바르지 않으면 에러 메시지를 보여주고 화면을 유지한다', (tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(ProviderScope(
      overrides: [apiClientProvider.overrideWithValue(fakeApiClient((options) async {
        return jsonResponse('{"error":"일치하는 회원이 없습니다."}', statusCode: 401);
      }))],
      child: const MaterialApp(home: LoginScreen()),
    ));
    await tester.enterText(find.byKey(const Key('employeeIdField')), '1001');
    await tester.enterText(find.byKey(const Key('phoneNumberField')), '01099999999');
    await tester.tap(find.widgetWithText(ElevatedButton, '로그인'));
    await tester.pumpAndSettle();

    expect(find.text('직번 또는 전화번호를 확인해주세요.'), findsOneWidget);
    expect(find.byType(ConsentScreen), findsNothing);
    expect(find.byType(LoginScreen), findsOneWidget);
  });
}
