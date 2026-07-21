import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/qr_login_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  test('정상 포맷 파싱', () {
    final result = parseQrPayload('1001:01012345678');
    expect(result?.employeeId, 1001);
    expect(result?.phoneNumber, '01012345678');
  });
  test('콜론 없으면 null', () {
    expect(parseQrPayload('invalid'), isNull);
  });
  test('직번이 숫자가 아니면 null', () {
    expect(parseQrPayload('abc:01012345678'), isNull);
  });

  group('QR 스캔 후 로그인 실패 시 재시도 제어', () {
    testWidgets('같은 QR이 로그인 실패 후에도 계속 인식되면 서버를 재호출하지 않는다', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      var loginAttempts = 0;
      await tester.pumpWidget(ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(fakeApiClient((options) async {
            loginAttempts++;
            return jsonResponse('{"error":"일치하는 회원이 없습니다."}', statusCode: 401);
          })),
        ],
        child: const MaterialApp(home: QrLoginScreen()),
      ));
      await tester.pump();

      final scanner = tester.widget<MobileScanner>(find.byType(MobileScanner));
      final onDetect = scanner.onDetect!;

      // 같은 원본 QR 값으로 계속 인식이 발생하는 상황을 흉내낸다
      // (MobileScanner는 프레임마다 onDetect를 호출하지만, 실제 카메라 없이
      // 콜백을 직접 호출해 이를 재현한다).
      final capture = BarcodeCapture(barcodes: [const Barcode(rawValue: '1001:01099999999')]);

      onDetect(capture);
      await tester.pumpAndSettle();
      expect(loginAttempts, 1);
      expect(find.text('일치하는 회원이 없습니다.'), findsOneWidget);

      // 카메라가 같은 QR을 다시 인식해도 더 이상 로그인 API를 호출하지 않아야 한다.
      onDetect(capture);
      await tester.pumpAndSettle();
      onDetect(capture);
      await tester.pumpAndSettle();
      onDetect(capture);
      await tester.pumpAndSettle();
      expect(loginAttempts, 1);
    });

    testWidgets('다시 시도 버튼을 누르면 같은 QR로도 재시도할 수 있다', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      var loginAttempts = 0;
      await tester.pumpWidget(ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(fakeApiClient((options) async {
            loginAttempts++;
            return jsonResponse('{"error":"일치하는 회원이 없습니다."}', statusCode: 401);
          })),
        ],
        child: const MaterialApp(home: QrLoginScreen()),
      ));
      await tester.pump();

      final scanner = tester.widget<MobileScanner>(find.byType(MobileScanner));
      final onDetect = scanner.onDetect!;
      final capture = BarcodeCapture(barcodes: [const Barcode(rawValue: '1001:01099999999')]);

      onDetect(capture);
      await tester.pumpAndSettle();
      expect(loginAttempts, 1);

      await tester.tap(find.widgetWithText(OutlinedButton, '다시 시도'));
      await tester.pumpAndSettle();

      onDetect(capture);
      await tester.pumpAndSettle();
      expect(loginAttempts, 2);
    });

    testWidgets('파싱 실패는 계속 재시도 가능하다 (서버 호출 없이 바로 에러만 갱신)', (tester) async {
      await tester.binding.setSurfaceSize(const Size(390, 844));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      var loginAttempts = 0;
      await tester.pumpWidget(ProviderScope(
        overrides: [
          apiClientProvider.overrideWithValue(fakeApiClient((options) async {
            loginAttempts++;
            return jsonResponse('{"accessToken":"tok","memberId":1}');
          })),
        ],
        child: const MaterialApp(home: QrLoginScreen()),
      ));
      await tester.pump();

      final scanner = tester.widget<MobileScanner>(find.byType(MobileScanner));
      final onDetect = scanner.onDetect!;
      final malformedCapture = BarcodeCapture(barcodes: [const Barcode(rawValue: 'not-a-valid-payload')]);

      onDetect(malformedCapture);
      await tester.pumpAndSettle();
      expect(find.text('QR 코드를 인식하지 못했어요. 다시 시도해주세요.'), findsOneWidget);
      expect(loginAttempts, 0);

      // 같은 malformed 값이 다시 들어와도 파싱 실패 경로는 계속 반응해야 한다 (재시도 버튼 없이도).
      onDetect(malformedCapture);
      await tester.pumpAndSettle();
      expect(find.text('QR 코드를 인식하지 못했어요. 다시 시도해주세요.'), findsOneWidget);
      expect(loginAttempts, 0);

      // 이제 유효한 QR을 인식하면 정상적으로 로그인 시도가 이루어진다.
      final validCapture = BarcodeCapture(barcodes: [const Barcode(rawValue: '1001:01012345678')]);
      onDetect(validCapture);
      await tester.pumpAndSettle();
      expect(loginAttempts, 1);
    });
  });
}
