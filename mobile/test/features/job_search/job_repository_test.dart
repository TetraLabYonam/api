import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/unit_type.dart';
import 'package:senior_job_attendance/features/job_search/job_repository.dart';

void main() {
  test('search parses PlaceSummary list from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter();
    final repository = JobRepository(dio: dio);

    final result = await repository.search(UnitType.publicInterest, '청소');

    expect(result.length, 1);
    expect(result.first.name, '공원안전지킴이');
  });
}

class _FakeAdapter implements HttpClientAdapter {
  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    return ResponseBody.fromString(
      '[{"id":1,"name":"공원안전지킴이","address":"주소","unitType":"PUBLIC_INTEREST","description":null,"latitude":35.3,"longitude":129.0}]',
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}
