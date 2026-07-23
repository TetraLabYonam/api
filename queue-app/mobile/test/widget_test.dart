import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:queue_app/main.dart';

class FakeRepository implements JobRepository {
  FakeRepository(this.jobs);

  final List<MemberJob> jobs;

  @override
  Future<TicketResult> issueTicket(MemberJob job, String phone) async =>
      const TicketResult(number: 42, duplicate: false);

  @override
  Future<List<MemberJob>> openJobs() async => jobs;
}

class FakeAdminApi implements AdminApi {
  String? token;

  @override
  Future<void> closeSession(String sessionUid) async {}

  @override
  Future<AdminJob> createJob(String title, String unitName) async =>
      AdminJob(id: 1, title: title, unitName: unitName);

  @override
  Future<List<AdminJob>> jobs() async => [];

  @override
  Future<void> openSession(int jobId) async {}

  @override
  void setToken(String value) => token = value;
}

const jobs = [
  MemberJob(id: 1, title: '안전 도우미', unitName: '중앙 사업단', sessionUid: 'a'),
  MemberJob(id: 2, title: '급식 지원', unitName: '행복 사업단', sessionUid: 'b'),
];

void main() {
  testWidgets('기본 시작 화면은 이용자와 관리자 역할을 선택한다', (tester) async {
    await tester.pumpWidget(const UnifiedApp());

    expect(find.text('이용자'), findsOneWidget);
    expect(find.text('관리자'), findsOneWidget);
  });

  testWidgets('역할 선택은 이용자 발급 흐름과 관리자 로그인으로 이동한다', (tester) async {
    final repository = FakeRepository(jobs);
    final api = FakeAdminApi();
    await tester.pumpWidget(UnifiedApp(repository: repository, adminApi: api));

    await tester.tap(find.text('이용자'));
    await tester.pumpAndSettle();
    expect(find.text('원하시는 일자리를 선택하세요'), findsOneWidget);

    await tester.tap(find.text('역할 선택'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('관리자'));
    await tester.pumpAndSettle();
    expect(find.text('운영 토큰'), findsOneWidget);

    await tester.tap(find.text('역할 선택으로 돌아가기'));
    await tester.pumpAndSettle();
    expect(find.text('이용자'), findsOneWidget);
  });

  testWidgets('injected domain token selects the requested flow', (
    tester,
  ) async {
    await tester.pumpWidget(
      UnifiedApp(domainToken: 'member', repository: FakeRepository(jobs)),
    );
    await tester.pumpAndSettle();
    expect(find.text('원하시는 일자리를 선택하세요'), findsOneWidget);

    await tester.pumpWidget(
      UnifiedApp(domainToken: 'admin', adminApi: FakeAdminApi()),
    );
    await tester.pumpAndSettle();
    expect(find.text('운영 토큰'), findsOneWidget);

    await tester.pumpWidget(const UnifiedApp(domainToken: 'unexpected'));
    await tester.pumpAndSettle();
    expect(find.text('이용자'), findsOneWidget);
    expect(find.text('관리자'), findsOneWidget);
  });

  testWidgets('일자리 목록은 선택 후 발급 CTA를 제공한다', (tester) async {
    await tester.pumpWidget(MemberApp(repository: FakeRepository(jobs)));
    await tester.pump();

    expect(find.text('안전 도우미'), findsOneWidget);
    expect(find.text('선택한 일자리로 순번표 받기'), findsOneWidget);
    expect(
      (tester.widget<FilledButton>(find.byType(FilledButton).last)).onPressed,
      isNull,
    );

    await tester.tap(find.text('안전 도우미'));
    await tester.pump();
    await tester.tap(find.text('선택한 일자리로 순번표 받기'));
    await tester.pumpAndSettle();
    expect(find.text('전화번호 입력하기'), findsOneWidget);
  });

  testWidgets('사업단 필터는 해당 사업단의 열린 일자리만 보인다', (tester) async {
    await tester.pumpWidget(MemberApp(repository: FakeRepository(jobs)));
    await tester.pump();
    await tester.tap(find.widgetWithText(ChoiceChip, '행복 사업단'));
    await tester.pump();

    expect(find.text('급식 지원'), findsOneWidget);
    expect(find.text('안전 도우미'), findsNothing);
  });

  testWidgets('관리자 로그인은 bearer 토큰을 API에 전달한다', (tester) async {
    final api = FakeAdminApi();
    await tester.pumpWidget(AdminApp(api: api));

    await tester.enterText(find.byType(TextField), 'bootstrap-token');
    await tester.tap(find.text('운영 시작'));
    await tester.pumpAndSettle();

    expect(api.token, 'bootstrap-token');
    expect(find.text('접수 운영'), findsOneWidget);
  });

  testWidgets('발급 및 중복 발급 성공 상태는 공용 번호와 안내를 보여준다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SuccessScreen(
          ticket: TicketResult(number: 728, duplicate: false),
          job: jobs[0],
        ),
      ),
    );
    expect(find.text('순번표가 발급되었습니다'), findsOneWidget);
    expect(find.text('728'), findsOneWidget);

    await tester.pumpWidget(
      MaterialApp(
        home: SuccessScreen(
          ticket: TicketResult(number: 728, duplicate: true),
          job: jobs[0],
        ),
      ),
    );
    expect(find.text('이미 발급된 순번표입니다'), findsOneWidget);
    expect(find.text('기존에 발급받은 번호를 보여드립니다.'), findsOneWidget);
  });
}
