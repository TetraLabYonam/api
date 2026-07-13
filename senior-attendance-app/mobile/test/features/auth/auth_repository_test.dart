import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/token_storage.dart';
import 'package:senior_job_attendance/features/auth/auth_repository.dart';

void main() {
  test('verifyOtp returns accessToken from response body', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter();
    final mockTokenStorage = _MockTokenStorage();
    final repository = AuthRepository(dio: dio, tokenStorage: mockTokenStorage);

    final token = await repository.verifyOtp('01012345678', '123456');

    expect(token, 'fake-access-token');
    expect(mockTokenStorage.savedToken, 'fake-access-token');
  });
}

class _FakeAdapter implements HttpClientAdapter {
  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
      RequestOptions options, Stream<List<int>>? requestStream, Future<void>? cancelFuture) async {
    return ResponseBody.fromString(
      '{"accessToken":"fake-access-token","memberId":1}',
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }
}

class _MockTokenStorage implements TokenStorage {
  String? savedToken;

  @override
  Future<void> saveAccessToken(String token) async {
    savedToken = token;
  }

  @override
  Future<String?> readAccessToken() async => savedToken;

  @override
  Future<void> clear() async {
    savedToken = null;
  }
}
