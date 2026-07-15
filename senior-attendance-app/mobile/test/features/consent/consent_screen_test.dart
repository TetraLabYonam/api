import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/consent/consent_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('네를 누르면 동의 후 출석 체크 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}'))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('출석 체크'), findsOneWidget);
  });

  testWidgets('제출 중 서버 오류가 나면 에러 안내를 보여주고 화면을 유지한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}', statusCode: 500))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('동의 처리에 실패했습니다. 다시 시도해주세요.'), findsOneWidget);
    expect(find.text('위치정보 수집 동의'), findsOneWidget);
  });

  testWidgets('아니오를 누르면 이전 화면으로 돌아간다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => ElevatedButton(
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const ConsentScreen()),
          ),
          child: const Text('열기'),
        ),
      ),
    ));

    await tester.tap(find.text('열기'));
    await tester.pumpAndSettle();
    expect(find.text('위치정보 수집 동의'), findsOneWidget);

    await tester.tap(find.widgetWithText(ElevatedButton, '아니오'));
    await tester.pumpAndSettle();

    expect(find.text('위치정보 수집 동의'), findsNothing);
    expect(find.text('열기'), findsOneWidget);
  });

  testWidgets('자세히 보기를 누르면 전문 약관 다이얼로그가 뜬다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: ConsentScreen()));

    await tester.tap(find.text('자세히 보기'));
    await tester.pumpAndSettle();

    expect(find.text('위치정보 수집 약관'), findsOneWidget);
    expect(find.textContaining('체크인 시점의 위치정보(GPS 좌표)를 수집합니다'), findsOneWidget);
  });
}
