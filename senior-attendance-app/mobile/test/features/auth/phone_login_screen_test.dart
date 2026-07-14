import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/phone_login_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('전화번호 입력 후 인증번호 받기를 누르면 OTP 요청 후 인증번호 입력 화면으로 이동한다', (tester) async {
    bool otpRequested = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/member-auth/otp/request') {
            otpRequested = true;
            return jsonResponse('{}');
          }
          throw StateError('예상치 못한 요청: ${options.path}');
        })),
      ],
      child: const MaterialApp(home: PhoneLoginScreen()),
    ));

    await tester.enterText(find.byType(TextField), '01012345678');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pumpAndSettle();

    expect(otpRequested, isTrue);
    expect(find.text('인증번호 6자리'), findsOneWidget);
  });

  testWidgets('요청 처리 중에는 버튼이 비활성화되고 전송 중으로 표시된다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          await Future<void>.delayed(const Duration(milliseconds: 100));
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: PhoneLoginScreen()),
    ));

    await tester.enterText(find.byType(TextField), '01012345678');
    await tester.tap(find.text('인증번호 받기'));
    await tester.pump();

    expect(find.text('전송 중...'), findsOneWidget);
    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.onPressed, isNull);

    await tester.pumpAndSettle();
  });
}
