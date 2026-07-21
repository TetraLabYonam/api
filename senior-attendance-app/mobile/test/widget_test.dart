import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/main.dart';

void main() {
  testWidgets('App boots and shows the login screen when logged out', (WidgetTester tester) async {
    // isLoggedInProvider's real TokenStorage() reads flutter_secure_storage's
    // platform channel, which has no handler in a plain widget test and never
    // resolves. Override it so this test never touches that channel.
    await tester.pumpWidget(ProviderScope(
      overrides: [isLoggedInProvider.overrideWith((ref) async => false)],
      child: const MyApp(),
    ));
    await tester.pumpAndSettle();

    expect(find.text('ATTENDANCE'), findsOneWidget);
    expect(find.text('로그인'), findsOneWidget);
  });
}
