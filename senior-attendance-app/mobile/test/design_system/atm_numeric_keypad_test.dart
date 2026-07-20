import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_numeric_keypad.dart';

void main() {
  testWidgets('숫자를 탭하면 onDigit이 해당 숫자로 호출된다', (tester) async {
    String? tapped;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(onDigit: (d) => tapped = d, onBackspace: () {}, onConfirm: () {}),
      ),
    ));

    await tester.tap(find.widgetWithText(OutlinedButton, '5'));
    expect(tapped, '5');
  });

  testWidgets('지우기 아이콘을 탭하면 onBackspace가 호출된다', (tester) async {
    bool backspaced = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(onDigit: (_) {}, onBackspace: () => backspaced = true, onConfirm: () {}),
      ),
    ));

    await tester.tap(find.byIcon(Icons.backspace_outlined));
    expect(backspaced, isTrue);
  });

  testWidgets('confirmLabel을 탭하면 onConfirm이 호출된다', (tester) async {
    bool confirmed = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(
          onDigit: (_) {},
          onBackspace: () {},
          onConfirm: () => confirmed = true,
          confirmLabel: '인증받기',
        ),
      ),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '인증받기'));
    expect(confirmed, isTrue);
  });

  testWidgets('onConfirm이 null이면 확인 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(onDigit: (_) {}, onBackspace: () {}, onConfirm: null),
      ),
    ));

    final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '확인'));
    expect(button.onPressed, isNull);
  });
}
