import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_option_list_item.dart';

void main() {
  testWidgets('제목과 부제목을 보여주고 탭하면 onTap이 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmOptionListItem(title: '사업단 A', subtitle: '설명', onTap: () => tapped = true),
      ),
    ));

    expect(find.text('사업단 A'), findsOneWidget);
    expect(find.text('설명'), findsOneWidget);
    await tester.tap(find.text('사업단 A'));
    expect(tapped, isTrue);
  });

  testWidgets('부제목이 없으면 표시하지 않는다', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: AtmOptionListItem(title: '사업단 A')),
    ));

    expect(find.text('사업단 A'), findsOneWidget);
  });
}
