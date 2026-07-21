import 'package:dio/dio.dart';
import '../../core/token_storage.dart';

class AuthRepository {
  final Dio dio;
  final TokenStorage tokenStorage;

  AuthRepository({required this.dio, TokenStorage? tokenStorage})
      : tokenStorage = tokenStorage ?? TokenStorage();

  Future<String> login(int employeeId, String phoneNumber) async {
    final response = await dio.post('/api/v1/member-auth/login', data: {
      'employeeId': employeeId,
      'phoneNumber': phoneNumber,
    });
    final accessToken = response.data['accessToken'] as String;
    await tokenStorage.saveAccessToken(accessToken);
    return accessToken;
  }

  Future<void> logout() => tokenStorage.clear();

  Future<({bool locationConsentAgreed, int? assignedPlaceId})> me() async {
    final response = await dio.get('/api/v1/members/me');
    final data = response.data as Map<String, dynamic>;
    return (
      locationConsentAgreed: data['locationConsentAgreed'] as bool,
      assignedPlaceId: data['assignedPlaceId'] is int ? data['assignedPlaceId'] as int : null,
    );
  }
}
