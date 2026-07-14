import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/unit_type.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/job_search/job_search_screen.dart';

import '../../support/fake_api_client.dart';

const _place1Json = '{"id":1,"name":"공원안전지킴이","address":"주소1",'
    '"unitType":"PUBLIC_INTEREST","description":null,"latitude":35.3,"longitude":129.0}';

Widget _wrap(Widget child, Future<ResponseBody> Function(RequestOptions) onFetch) {
  return ProviderScope(
    overrides: [apiClientProvider.overrideWithValue(fakeApiClient(onFetch))],
    child: MaterialApp(home: child),
  );
}

void main() {
  testWidgets('화면 진입 시 해당 유형의 일자리 목록이 자동으로 뜬다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async => jsonResponse('[$_place1Json]'),
    ));
    await tester.pumpAndSettle();

    expect(find.text('공원안전지킴이'), findsOneWidget);
  });

  testWidgets('목록 로드에 실패하면 에러 안내를 보여준다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async => jsonResponse('{"message":"실패"}', statusCode: 500),
    ));
    await tester.pumpAndSettle();

    expect(find.text('일자리 목록을 불러오지 못했습니다. 다시 시도해주세요.'), findsOneWidget);
  });

  testWidgets('검색 결과가 0건이면 AI로 더 찾아보기 버튼이 뜬다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async {
        if (options.queryParameters['q'] == '존재안함') {
          return jsonResponse('[]');
        }
        return jsonResponse('[$_place1Json]');
      },
    ));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '존재안함');
    await tester.tap(find.byIcon(Icons.search));
    await tester.pumpAndSettle();

    expect(find.text('AI로 더 찾아보기'), findsOneWidget);
  });

  testWidgets('AI로 더 찾아보기를 탭하면 AI 폴백 검색 결과로 목록이 갱신된다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async {
        if (options.path == '/api/v1/places/search/fallback') {
          return jsonResponse('[$_place1Json]');
        }
        return jsonResponse('[]');
      },
    ));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '학교 앞에서 깃발');
    await tester.tap(find.byIcon(Icons.search));
    await tester.pumpAndSettle();

    await tester.tap(find.text('AI로 더 찾아보기'));
    await tester.pumpAndSettle();

    expect(find.text('공원안전지킴이'), findsOneWidget);
  });

  testWidgets('목록에서 항목을 탭하면 본인 일자리로 등록하고 동의 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async {
        if (options.path == '/api/v1/members/me/assign-place') {
          return jsonResponse('{}');
        }
        return jsonResponse('[$_place1Json]');
      },
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.text('공원안전지킴이'));
    await tester.pumpAndSettle();

    expect(find.text('위치정보 수집 동의'), findsOneWidget);
  });
}
