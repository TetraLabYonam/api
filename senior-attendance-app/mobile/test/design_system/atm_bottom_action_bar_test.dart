// mobile/test/design_system/atm_bottom_action_bar_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_bottom_action_bar.dart';

void main() {
  testWidgets('single 변형은 라벨 하나만 보여주고 탭하면 콜백이 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        bottomNavigationBar: AtmBottomActionBar.single(label: '이전', onPressed: () => tapped = true),
      ),
    ));

    expect(find.text('이전'), findsOneWidget);
    expect(find.text('네'), findsNothing);
    await tester.tap(find.text('이전'));
    expect(tapped, isTrue);
  });

  testWidgets('confirm 변형은 네/아니오 버튼을 보여주고 각각 콜백을 호출한다', (tester) async {
    bool yesTapped = false;
    bool noTapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        bottomNavigationBar: AtmBottomActionBar.confirm(
          onYes: () => yesTapped = true,
          onNo: () => noTapped = true,
        ),
      ),
    ));

    await tester.tap(find.text('네'));
    expect(yesTapped, isTrue);

    await tester.tap(find.text('아니오'));
    expect(noTapped, isTrue);
  });

  testWidgets('confirm 변형에서 콜백이 null이면 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(
        bottomNavigationBar: AtmBottomActionBar.confirm(onYes: null, onNo: null),
      ),
    ));

    final buttons = tester.widgetList<ElevatedButton>(find.byType(ElevatedButton));
    expect(buttons.every((b) => b.onPressed == null), isTrue);
  });
}
