import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/consent/consent_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('동의 체크 전에는 계속하기 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: MaterialApp(home: ConsentScreen())));

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.onPressed, isNull);
  });

  testWidgets('동의 체크 후 제출에 성공하면 출석 체크 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}'))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.byType(CheckboxListTile));
    await tester.pump();
    await tester.tap(find.text('동의하고 계속하기'));
    await tester.pumpAndSettle();

    expect(find.text('출석 체크'), findsNWidgets(2));
  });

  testWidgets('제출 중 서버 오류가 나면 에러 안내를 보여주고 화면을 유지한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}', statusCode: 500))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.byType(CheckboxListTile));
    await tester.pump();
    await tester.tap(find.text('동의하고 계속하기'));
    await tester.pumpAndSettle();

    expect(find.text('동의 처리에 실패했습니다. 다시 시도해주세요.'), findsOneWidget);
    expect(find.text('위치정보 수집 동의'), findsOneWidget);
  });
}
