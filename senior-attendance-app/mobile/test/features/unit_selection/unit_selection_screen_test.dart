import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/unit_type.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/unit_selection/unit_selection_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('사업단 유형 개수만큼 선택 버튼이 뜬다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: UnitSelectionScreen()));

    for (final type in UnitType.values) {
      expect(find.text(type.label), findsOneWidget);
    }
  });

  testWidgets('유형 하나를 탭하면 해당 유형의 일자리 검색 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('[]'))),
      ],
      child: const MaterialApp(home: UnitSelectionScreen()),
    ));

    await tester.tap(find.text(UnitType.market.label));
    await tester.pumpAndSettle();

    expect(find.text('${UnitType.market.label} 일자리 찾기'), findsOneWidget);
  });

  testWidgets('질문 문구와 이전 버튼이 보인다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: UnitSelectionScreen()));

    expect(find.text('사업단을 선택해주세요'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, '이전'), findsOneWidget);
  });
}
