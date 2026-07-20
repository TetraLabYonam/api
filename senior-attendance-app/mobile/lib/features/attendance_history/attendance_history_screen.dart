import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('이번 달 출석 이력')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _errorMessage != null
          ? Center(
              child: Text(_errorMessage!, style: const TextStyle(color: Colors.red)),
            )
          : Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '이번 달 출석률: ${_history!.attendanceRate.toStringAsFixed(1)}%',
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.black),
                  ),
                  const SizedBox(height: 20),
                  Expanded(
                    child: _history!.records.isEmpty
                        ? const Center(
                            child: Text('이번 달 출석 기록이 없습니다', style: TextStyle(color: Colors.black54)),
                          )
                        : ListView.separated(
                            itemCount: _history!.records.length,
                            separatorBuilder: (_, __) => const Divider(),
                            itemBuilder: (context, index) {
                              final record = _history!.records[index];
                              return ListTile(
                                contentPadding: EdgeInsets.zero,
                                title: Text(record.scheduleDate),
                                subtitle: Text(record.placeName ?? ''),
                                trailing: Text(_statusLabel(record.status)),
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
