import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/attendance_history/attendance_history_repository.dart';

void main() {
  test('getHistory parses attendanceRate and records from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter('''
    {
      "attendanceRate": 75.0,
      "records": [
        {"scheduleDate": "2026-07-01", "placeName": "중앙공원", "status": "PRESENT"},
        {"scheduleDate": "2026-07-08", "placeName": "중앙공원", "status": "ABSENT"},
        {"scheduleDate": "2026-07-15", "placeName": "행복복지관", "status": "LATE"}
      ]
    }
    ''');
    final repository = AttendanceHistoryRepository(dio: dio);

    final history = await repository.getHistory();

    expect(history.attendanceRate, 75.0);
    expect(history.records, hasLength(3));
    expect(history.records[0].scheduleDate, '2026-07-01');
    expect(history.records[0].placeName, '중앙공원');
    expect(history.records[0].status, 'PRESENT');
    expect(history.records[1].status, 'ABSENT');
    expect(history.records[2].status, 'LATE');
  });

  test('getHistory parses empty records response', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter('{"attendanceRate":0.0,"records":[]}');
    final repository = AttendanceHistoryRepository(dio: dio);

    final history = await repository.getHistory();

    expect(history.attendanceRate, 0.0);
    expect(history.records, isEmpty);
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
    RequestOptions options,
    Stream<List<int>>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    return ResponseBody.fromString(
      body,
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}
