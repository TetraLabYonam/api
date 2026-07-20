import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/checkin/checkin_repository.dart';

void main() {
  test('checkIn parses success result from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"success":true,"message":"출석 처리되었습니다.","distance":42.5}',
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.checkIn(scheduleId: 1, latitude: 35.3, longitude: 129.0);

    expect(result.success, isTrue);
    expect(result.message, '출석 처리되었습니다.');
  });

  test('checkIn surfaces the server error message on 409', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"message":"출석 가능한 위치가 아닙니다. (거리: 800.0m, 허용 반경: 500m)"}',
      statusCode: 409,
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.checkIn(scheduleId: 1, latitude: 0, longitude: 0);

    expect(result.success, isFalse);
    expect(result.message, contains('허용 반경'));
  });

  test('decline parses success result from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"success":true,"message":"결석 처리되었습니다."}',
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.decline(scheduleId: 1);

    expect(result.success, isTrue);
    expect(result.message, '결석 처리되었습니다.');
  });

  test('decline surfaces already-attended message with success false', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"success":false,"message":"이미 출석 처리되었습니다."}',
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.decline(scheduleId: 1);

    expect(result.success, isFalse);
    expect(result.message, '이미 출석 처리되었습니다.');
  });

  test('decline surfaces the server error message on failure status', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"message":"해당 일정의 출석 정보를 찾을 수 없습니다."}',
      statusCode: 404,
    );
    final repository = CheckinRepository(dio: dio);

    final result = await repository.decline(scheduleId: 1);

    expect(result.success, isFalse);
    expect(result.message, contains('찾을 수 없습니다'));
  });

  test('getTodayAttend parses hasSchedule=true response', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"hasSchedule":true,"scheduleId":12,"placeName":"중앙공원","startTime":"09:00","endTime":"13:00"}',
    );
    final repository = CheckinRepository(dio: dio);

    final today = await repository.getTodayAttend();

    expect(today.hasSchedule, isTrue);
    expect(today.scheduleId, 12);
    expect(today.placeName, '중앙공원');
    expect(today.startTime, '09:00');
    expect(today.endTime, '13:00');
  });

  test('getTodayAttend parses hasSchedule=false response', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter('{"hasSchedule":false}');
    final repository = CheckinRepository(dio: dio);

    final today = await repository.getTodayAttend();

    expect(today.hasSchedule, isFalse);
    expect(today.scheduleId, isNull);
  });
}

class _FakeAdapter implements HttpClientAdapter {
  final String body;
  final int statusCode;
  _FakeAdapter(this.body, {this.statusCode = 200});

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    return ResponseBody.fromString(body, statusCode, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }
}
