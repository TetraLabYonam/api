import 'package:dio/dio.dart';
import 'package:senior_job_attendance/core/api_client.dart';
import 'package:senior_job_attendance/core/token_storage.dart';

/// 위젯 테스트에서 flutter_secure_storage 플랫폼 채널을 타지 않도록 하는 가짜 저장소.
class FakeSecureStore implements SecureStore {
  @override
  Future<void> write({required String key, required String? value}) async {}

  @override
  Future<String?> read({required String key}) async => 'test-access-token';
}

/// 요청 경로/쿼리를 보고 원하는 응답(또는 예외)을 콜백으로 정의하는 가짜 어댑터.
class CallbackAdapter implements HttpClientAdapter {
  final Future<ResponseBody> Function(RequestOptions options) onFetch;
  CallbackAdapter(this.onFetch);

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) {
    return onFetch(options);
  }
}

ResponseBody jsonResponse(String body, {int statusCode = 200}) {
  return ResponseBody.fromString(body, statusCode, headers: {
    Headers.contentTypeHeader: [Headers.jsonContentType],
  });
}

/// apiClientProvider를 오버라이드할 때 쓰는 가짜 ApiClient.
/// [onFetch]에서 options.path/queryParameters를 보고 원하는 응답을 반환하거나 예외를 던지면 된다.
ApiClient fakeApiClient(Future<ResponseBody> Function(RequestOptions options) onFetch) {
  final client = ApiClient(baseUrl: 'http://test', tokenStorage: TokenStorage(FakeSecureStore()));
  client.dio.httpClientAdapter = CallbackAdapter(onFetch);
  return client;
}
