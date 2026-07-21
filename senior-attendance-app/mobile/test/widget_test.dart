import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/checkin/checkin_screen.dart';
import 'package:senior_job_attendance/features/consent/consent_screen.dart';
import 'package:senior_job_attendance/main.dart';

import 'support/fake_api_client.dart';

void main() {
  testWidgets('로그아웃 상태이면 로그인 화면(직번/전화번호 입력)을 보여준다', (WidgetTester tester) async {
    // isLoggedInProvider's real TokenStorage() reads flutter_secure_storage's
    // platform channel, which has no handler in a plain widget test and never
    // resolves. Override it so this test never touches that channel.
    await tester.pumpWidget(ProviderScope(
      overrides: [isLoggedInProvider.overrideWith((ref) async => false)],
      child: const MyApp(),
    ));
    await tester.pumpAndSettle();

    expect(find.text('ATTENDANCE'), findsOneWidget);
    expect(find.text('직번'), findsOneWidget);
    expect(find.text('전화번호'), findsOneWidget);
  });

  testWidgets('로그인 상태이지만 위치정보 동의 전이면 동의 화면을 보여준다', (WidgetTester tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        isLoggedInProvider.overrideWith((ref) async => true),
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/members/me') {
            return jsonResponse('{"locationConsentAgreed":false,"assignedPlaceId":""}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MyApp(),
    ));
    await tester.pumpAndSettle();

    expect(find.byType(ConsentScreen), findsOneWidget);
  });

  testWidgets('로그인 상태이고 위치정보 동의를 마쳤으면 체크인 화면을 보여준다', (WidgetTester tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        isLoggedInProvider.overrideWith((ref) async => true),
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/members/me') {
            return jsonResponse('{"locationConsentAgreed":true,"assignedPlaceId":1}');
          }
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":false}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MyApp(),
    ));
    await tester.pumpAndSettle();

    expect(find.byType(CheckinScreen), findsOneWidget);
  });

  testWidgets('회원 정보 조회(me)가 실패하면 로그인 화면으로 돌아간다', (WidgetTester tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        isLoggedInProvider.overrideWith((ref) async => true),
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"error":"unauthorized"}', statusCode: 401);
        })),
      ],
      child: const MyApp(),
    ));
    await tester.pumpAndSettle();

    expect(find.text('ATTENDANCE'), findsOneWidget);
    expect(find.text('직번'), findsOneWidget);
    expect(find.text('전화번호'), findsOneWidget);
  });
}
