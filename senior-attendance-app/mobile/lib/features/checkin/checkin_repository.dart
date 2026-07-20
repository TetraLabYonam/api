import 'package:dio/dio.dart';

class TodayAttend {
  final bool hasSchedule;
  final int? scheduleId;
  final String? placeName;
  final String? startTime;
  final String? endTime;

  TodayAttend({
    required this.hasSchedule,
    this.scheduleId,
    this.placeName,
    this.startTime,
    this.endTime,
  });

  factory TodayAttend.fromJson(Map<String, dynamic> json) {
    return TodayAttend(
      hasSchedule: json['hasSchedule'] as bool? ?? false,
      scheduleId: json['scheduleId'] as int?,
      placeName: json['placeName'] as String?,
      startTime: json['startTime'] as String?,
      endTime: json['endTime'] as String?,
    );
  }
}

class CheckinResult {
  final bool success;
  final String message;

  CheckinResult({required this.success, required this.message});
}

class CheckinRepository {
  final Dio dio;

  CheckinRepository({required this.dio});

  Future<TodayAttend> getTodayAttend() async {
    final response = await dio.get('/api/v1/attend/today');
    return TodayAttend.fromJson(response.data as Map<String, dynamic>);
  }

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

  Future<CheckinResult> decline({required int scheduleId}) async {
    try {
      final response = await dio.post('/api/v1/attend/decline', data: {
        'scheduleId': scheduleId,
      });
      return CheckinResult(
        success: response.data['success'] as bool? ?? true,
        message: response.data['message'] as String? ?? '결석 처리되었습니다.',
      );
    } on DioException catch (e) {
      final message = e.response?.data is Map
          ? (e.response?.data['message'] as String? ?? '결석 처리에 실패했습니다.')
          : '결석 처리에 실패했습니다.';
      return CheckinResult(success: false, message: message);
    }
  }
}
