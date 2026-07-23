import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:queue_app/main.dart';

const _baseUrl = String.fromEnvironment('API_BASE_URL');
const _adminToken = String.fromEnvironment('E2E_ADMIN_TOKEN');
const _deviceId = String.fromEnvironment('E2E_DEVICE_ID');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('run-scoped job opens, issues a ticket, and closes', (
    tester,
  ) async {
    // Setup: use one explicitly selected Android device, for example:
    // flutter devices; flutter test integration_test/queue_flow_test.dart \
    //   -d <device-id> --dart-define=E2E_DEVICE_ID=<device-id> \
    //   --dart-define=API_BASE_URL=http://10.0.2.2:8080 \
    //   --dart-define=E2E_ADMIN_TOKEN=<bootstrap-token>
    expect(
      _deviceId,
      isNotEmpty,
      reason: 'E2E_DEVICE_ID must select one device',
    );
    expect(_baseUrl, isNotEmpty, reason: 'API_BASE_URL is required');
    expect(_adminToken, isNotEmpty, reason: 'E2E_ADMIN_TOKEN is required');

    final api = HttpAdminApi(baseUrl: _baseUrl)..setToken(_adminToken);
    final title = 'E2E ${DateTime.now().toUtc().microsecondsSinceEpoch}';
    final unit = 'E2E fixture';
    final created = await api.createJob(title, unit);
    await api.openSession(created.id);
    final opened = (await api.jobs()).singleWhere(
      (job) => job.id == created.id,
    );
    expect(opened.sessionUid, isNotNull);

    try {
      await tester.pumpWidget(
        MemberApp(repository: HttpJobRepository(baseUrl: _baseUrl)),
      );
      await tester.pumpAndSettle();
      expect(find.text(title), findsOneWidget);
      await tester.tap(find.text(title));
      await tester.tap(find.text('선택한 일자리로 순번표 받기'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('전화번호 입력하기'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), '+821012345678');
      await tester.tap(find.text('순번표 발급받기'));
      await tester.pumpAndSettle();
      expect(find.text('순번표가 발급되었습니다'), findsOneWidget);
    } finally {
      await api.closeSession(opened.sessionUid!);
    }
  });
}
