import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/consent/consent_repository.dart';

void main() {
  test('agree posts to the consent endpoint', () async {
    final dio = Dio();
    late String calledPath;
    dio.httpClientAdapter = _RecordingAdapter(onFetch: (path) => calledPath = path);
    final repository = ConsentRepository(dio: dio);

    await repository.agree();

    expect(calledPath, '/api/v1/members/me/consent');
  });
}

class _RecordingAdapter implements HttpClientAdapter {
  final void Function(String path) onFetch;
  _RecordingAdapter({required this.onFetch});

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    onFetch(options.path);
    return ResponseBody.fromString('{"message":"ok"}', 200, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }
}
