import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/otp_verify_screen.dart';
import 'package:senior_job_attendance/features/unit_selection/unit_selection_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  Widget appWithRoutes(Widget home) {
    return MaterialApp(
      home: home,
      routes: {
        '/unit-selection': (context) => const UnitSelectionScreen(),
      },
    );
  }

  testWidgets('정확한 인증번호를 입력하면 사업단 유형 선택 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/member-auth/otp/verify') {
            return jsonResponse('{"accessToken":"fake-token","memberId":1}');
          }
          throw StateError('예상치 못한 요청: ${options.path}');
        })),
      ],
      child: appWithRoutes(const OtpVerifyScreen(phoneNumber: '01012345678')),
    ));

    await tester.enterText(find.byType(TextField), '123456');
    await tester.tap(find.text('확인'));
    await tester.pumpAndSettle();

    expect(find.text('사업단 유형 선택'), findsOneWidget);
  });

  testWidgets('틀린 인증번호를 입력하면 에러 메시지를 보여주고 화면을 유지한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"error":"인증번호가 올바르지 않습니다."}', statusCode: 401);
        })),
      ],
      child: appWithRoutes(const OtpVerifyScreen(phoneNumber: '01012345678')),
    ));

    await tester.enterText(find.byType(TextField), '000000');
    await tester.tap(find.text('확인'));
    await tester.pumpAndSettle();

    expect(find.text('인증번호가 올바르지 않습니다.'), findsOneWidget);
    expect(find.text('인증번호 6자리'), findsOneWidget);
  });
}
