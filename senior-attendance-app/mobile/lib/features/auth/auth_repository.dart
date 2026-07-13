import 'package:dio/dio.dart';
import '../../core/token_storage.dart';

class AuthRepository {
  final Dio dio;
  final TokenStorage tokenStorage;

  AuthRepository({required this.dio, TokenStorage? tokenStorage})
      : tokenStorage = tokenStorage ?? TokenStorage();

  Future<void> requestOtp(String phoneNumber) async {
    await dio.post('/api/v1/member-auth/otp/request', data: {'phoneNumber': phoneNumber});
  }

  Future<String> verifyOtp(String phoneNumber, String code) async {
    final response = await dio.post('/api/v1/member-auth/otp/verify', data: {
      'phoneNumber': phoneNumber,
      'code': code,
    });
    final accessToken = response.data['accessToken'] as String;
    await tokenStorage.saveAccessToken(accessToken);
    return accessToken;
  }
}
