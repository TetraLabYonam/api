import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_colors.dart';
import 'package:senior_job_attendance/design_system/atm_secondary_button.dart';

void main() {
  testWidgets('라벨을 표시하고 탭하면 onPressed가 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmSecondaryButton(label: '취소', onPressed: () => tapped = true)),
    ));

    expect(find.text('취소'), findsOneWidget);
    await tester.tap(find.byType(AtmSecondaryButton));
    expect(tapped, isTrue);
  });

  testWidgets('흰 배경에 검정 테두리로 렌더링된다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmSecondaryButton(label: '취소', onPressed: () {})),
    ));

    final button = tester.widget<OutlinedButton>(find.byType(OutlinedButton));
    expect(button.style?.backgroundColor?.resolve({}), AtmColors.onPrimary);
    expect(button.style?.side?.resolve({})?.color, AtmColors.border);
  });
}
