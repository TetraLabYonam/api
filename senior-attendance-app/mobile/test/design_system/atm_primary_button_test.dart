// mobile/test/design_system/atm_primary_button_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_colors.dart';
import 'package:senior_job_attendance/design_system/atm_primary_button.dart';

void main() {
  testWidgets('라벨을 표시하고 탭하면 onPressed가 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmPrimaryButton(label: '확인', onPressed: () => tapped = true)),
    ));

    expect(find.text('확인'), findsOneWidget);
    await tester.tap(find.byType(AtmPrimaryButton));
    expect(tapped, isTrue);
  });

  testWidgets('onPressed가 null이면 비활성화된다', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: AtmPrimaryButton(label: '확인', onPressed: null)),
    ));

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.onPressed, isNull);
  });

  testWidgets('그린 배경으로 렌더링된다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmPrimaryButton(label: '확인', onPressed: () {})),
    ));

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.style?.backgroundColor?.resolve({}), AtmColors.primary);
  });
}
