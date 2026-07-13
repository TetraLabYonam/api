import 'package:dio/dio.dart';

class CheckinResult {
  final bool success;
  final String message;

  CheckinResult({required this.success, required this.message});
}

class CheckinRepository {
  final Dio dio;

  CheckinRepository({required this.dio});

  Future<CheckinResult> checkIn({
    required int scheduleId,
    required double latitude,
    required double longitude,
  }) async {
    try {
      final response = await dio.post('/api/v1/attend/check-in', data: {
        'scheduleId': scheduleId,
        'latitude': latitude,
        'longitude': longitude,
      });
      return CheckinResult(
        success: response.data['success'] as bool? ?? true,
        message: response.data['message'] as String? ?? '출석 처리되었습니다.',
      );
    } on DioException catch (e) {
      final message = e.response?.data is Map
          ? (e.response?.data['message'] as String? ?? '출석 처리에 실패했습니다.')
          : '출석 처리에 실패했습니다.';
      return CheckinResult(success: false, message: message);
    }
  }
}
