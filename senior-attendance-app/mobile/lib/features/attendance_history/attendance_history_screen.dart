import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_colors.dart';
import '../auth/auth_provider.dart';
import 'attendance_history_repository.dart';

class AttendanceHistoryScreen extends ConsumerStatefulWidget {
  const AttendanceHistoryScreen({super.key});

  @override
  ConsumerState<AttendanceHistoryScreen> createState() => _AttendanceHistoryScreenState();
}

class _AttendanceHistoryScreenState extends ConsumerState<AttendanceHistoryScreen> {
  bool _loading = true;
  AttendHistory? _history;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final repo = AttendanceHistoryRepository(dio: ref.read(apiClientProvider).dio);
      final history = await repo.getHistory();
      if (!mounted) return;
      setState(() => _history = history);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorMessage = '출석 이력을 불러오지 못했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String _statusLabel(String status) {
    switch (status) {
      case 'SCHEDULED':
        return '출석 예정';
      case 'PRESENT':
        return '출석';
      case 'ABSENT':
        return '결석';
      case 'LATE':
        return '지각';
      case 'EXCUSED':
        return '사유 인정';
      default:
        return status;
    }
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'PRESENT':
        return AtmColors.primary;
      case 'ABSENT':
        return AtmColors.error;
      case 'LATE':
        return AtmColors.onSurfaceVariant;
      default:
        return AtmColors.onSurfaceVariant;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('출석 이력')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
          ? Center(
              child: Text(_errorMessage!, style: const TextStyle(color: AtmColors.error)),
            )
          : Padding(
              padding: const EdgeInsets.fromLTRB(24, 20, 24, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      border: Border.all(color: AtmColors.border, width: 2),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('이번 달 출석률', style: TextStyle(fontSize: 16, color: AtmColors.onSurfaceVariant)),
                        const SizedBox(height: 4),
                        Text(
                          '${_history!.attendanceRate.toStringAsFixed(1)}%',
                          style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold, color: AtmColors.primary),
                        ),
                        const SizedBox(height: 12),
                        ClipRRect(
                          borderRadius: BorderRadius.circular(4),
                          child: LinearProgressIndicator(
                            value: (_history!.attendanceRate / 100).clamp(0.0, 1.0),
                            minHeight: 8,
                            backgroundColor: AtmColors.surface,
                            valueColor: const AlwaysStoppedAnimation(AtmColors.primary),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),
                  Expanded(
                    child: _history!.records.isEmpty
                        ? const Center(
                            child: Text('이번 달 출석 기록이 없습니다', style: TextStyle(color: AtmColors.onSurfaceVariant)),
                          )
                        : ListView.separated(
                            padding: const EdgeInsets.only(bottom: 20),
                            itemCount: _history!.records.length,
                            separatorBuilder: (_, __) => const SizedBox(height: 12),
                            itemBuilder: (context, index) {
                              final record = _history!.records[index];
                              return Container(
                                width: double.infinity,
                                padding: const EdgeInsets.all(16),
                                decoration: BoxDecoration(
                                  border: Border.all(color: AtmColors.border, width: 2),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Row(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(record.scheduleDate,
                                              style: const TextStyle(fontSize: 14, color: AtmColors.onSurfaceVariant)),
                                          const SizedBox(height: 4),
                                          Text(record.placeName ?? '',
                                              style: const TextStyle(
                                                  fontSize: 18, fontWeight: FontWeight.bold, color: AtmColors.primary)),
                                        ],
                                      ),
                                    ),
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                                      decoration: BoxDecoration(
                                        color: _statusColor(record.status),
                                        borderRadius: BorderRadius.circular(6),
                                      ),
                                      child: Text(_statusLabel(record.status),
                                          style: const TextStyle(
                                              fontSize: 14, fontWeight: FontWeight.bold, color: AtmColors.onPrimary)),
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),
                  ),
                ],
              ),
            ),
    );
  }
}
