import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/phone_login_screen.dart';

import '../../support/fake_api_client.dart';

Future<void> _enterDigits(WidgetTester tester, String digits) async {
  for (final digit in digits.split('')) {
    await tester.tap(find.widgetWithText(OutlinedButton, digit));
    await tester.pump();
  }
}

void main() {
  Future<void> useDeviceSurface(WidgetTester tester) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
  }

  testWidgets('전화번호 입력 후 인증받기를 누르면 OTP 요청 후 인증번호 입력 화면으로 이동한다', (tester) async {
    await useDeviceSurface(tester);
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

    await _enterDigits(tester, '01012345678');
    await tester.tap(find.widgetWithText(ElevatedButton, '인증받기'));
    await tester.pumpAndSettle();

    expect(otpRequested, isTrue);
    expect(find.textContaining('인증번호 6자리를'), findsOneWidget);
  });

  testWidgets('전화번호를 입력하지 않으면 인증받기 버튼이 비활성화된다', (tester) async {
    await useDeviceSurface(tester);
    await tester.pumpWidget(const ProviderScope(child: MaterialApp(home: PhoneLoginScreen())));

    final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '인증받기'));
    expect(button.onPressed, isNull);
  });

  testWidgets('요청 처리 중에는 확인 버튼이 비활성화되고 전송 중으로 표시된다', (tester) async {
    await useDeviceSurface(tester);
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          await Future<void>.delayed(const Duration(milliseconds: 100));
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: PhoneLoginScreen()),
    ));

    await _enterDigits(tester, '01012345678');
    await tester.tap(find.widgetWithText(ElevatedButton, '인증받기'));
    await tester.pump();

    expect(find.text('전송 중...'), findsOneWidget);
    final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '전송 중...'));
    expect(button.onPressed, isNull);

    await tester.pumpAndSettle();
  });

  testWidgets('취소를 누르면 입력한 전화번호가 지워진다', (tester) async {
    await useDeviceSurface(tester);
    await tester.pumpWidget(const ProviderScope(child: MaterialApp(home: PhoneLoginScreen())));

    await _enterDigits(tester, '010');
    expect(find.text('010'), findsOneWidget);

    await tester.tap(find.widgetWithText(OutlinedButton, '취소'));
    await tester.pump();

    expect(find.text('010'), findsNothing);
  });
}
