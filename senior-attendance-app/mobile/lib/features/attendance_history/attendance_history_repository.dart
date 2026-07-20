import 'package:dio/dio.dart';

class AttendHistoryRecord {
  final String scheduleDate;
  final String? placeName;
  final String status;

  AttendHistoryRecord({required this.scheduleDate, this.placeName, required this.status});

  factory AttendHistoryRecord.fromJson(Map<String, dynamic> json) {
    return AttendHistoryRecord(
      scheduleDate: json['scheduleDate'] as String? ?? '',
      placeName: json['placeName'] as String?,
      status: json['status'] as String? ?? '',
    );
  }
}

class AttendHistory {
  final double attendanceRate;
  final List<AttendHistoryRecord> records;

  AttendHistory({required this.attendanceRate, required this.records});

  factory AttendHistory.fromJson(Map<String, dynamic> json) {
    final rawRecords = json['records'] as List<dynamic>? ?? [];
    return AttendHistory(
      attendanceRate: (json['attendanceRate'] as num?)?.toDouble() ?? 0.0,
      records: rawRecords.map((e) => AttendHistoryRecord.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}

class AttendanceHistoryRepository {
  final Dio dio;

  AttendanceHistoryRepository({required this.dio});

  Future<AttendHistory> getHistory() async {
    final response = await dio.get('/api/v1/attend/history');
    return AttendHistory.fromJson(response.data as Map<String, dynamic>);
  }
}
