import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../attendance_history/attendance_history_screen.dart';
import '../auth/auth_provider.dart';
import '../auth/login_screen.dart';
import 'checkin_repository.dart';

class CheckinScreen extends ConsumerStatefulWidget {
  const CheckinScreen({super.key});

  @override
  ConsumerState<CheckinScreen> createState() => _CheckinScreenState();
}

class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  bool _loadingToday = true;
  TodayAttend? _today;
  bool _checkingIn = false;
  bool _declining = false;
  String? _errorMessage;
  CheckinResult? _result;

  @override
  void initState() {
    super.initState();
    _loadToday();
  }

  Future<void> _loadToday() async {
    setState(() => _loadingToday = true);
    try {
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final today = await repo.getTodayAttend();
      if (!mounted) return;
      setState(() => _today = today);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorMessage = '오늘 일정을 불러오지 못했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loadingToday = false);
    }
  }

  Future<void> _confirmCheckIn() async {
    final scheduleId = _today?.scheduleId;
    if (scheduleId == null || _checkingIn) return;

    setState(() {
      _checkingIn = true;
      _errorMessage = null;
    });
    try {
      final permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        if (!mounted) return;
        setState(() => _errorMessage = '위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.');
        return;
      }

      final position = await Geolocator.getCurrentPosition();
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final result = await repo.checkIn(
        scheduleId: scheduleId,
        latitude: position.latitude,
        longitude: position.longitude,
      );
      if (!mounted) return;
      setState(() => _result = result);
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = '위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.');
      }
    } finally {
      if (mounted) setState(() => _checkingIn = false);
    }
  }

  Future<void> _confirmLogout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('로그아웃'),
        content: const Text('로그아웃하시겠어요?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('취소'),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('로그아웃'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    await ref.read(authRepositoryProvider).logout();
    ref.invalidate(isLoggedInProvider);
    ref.invalidate(meProvider);
    if (!mounted) return;
    Navigator.of(context).pushAndRemoveUntil(
      MaterialPageRoute(builder: (_) => const LoginScreen()),
      (route) => false,
    );
  }

  Future<void> _declineCheckIn() async {
    final scheduleId = _today?.scheduleId;
    if (scheduleId == null || _declining) return;

    setState(() {
      _declining = true;
      _errorMessage = null;
    });
    try {
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final result = await repo.decline(scheduleId: scheduleId);
      if (!mounted) return;
      setState(() => _result = result);
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = '결석 처리에 실패했습니다. 다시 시도해주세요.');
      }
    } finally {
      if (mounted) setState(() => _declining = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_result != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('출석 체크')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  _result!.success ? Icons.check_circle_outline : Icons.info_outline,
                  color: AtmColors.primary,
                  size: 56,
                ),
                const SizedBox(height: 16),
                Text(
                  _result!.message,
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AtmColors.primary),
                ),
              ],
            ),
          ),
        ),
        bottomNavigationBar: Padding(
          padding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
          child: AtmBottomActionBar.single(
            label: '확인',
            onPressed: () => Navigator.of(context).popUntil((route) => route.isFirst),
          ),
        ),
      );
    }

    final canAct = _today != null && _today!.hasSchedule;

    return Scaffold(
      appBar: AppBar(
        title: const Text('출석 체크'),
        actions: [
          IconButton(
            icon: const Icon(Icons.history),
            tooltip: '이번 달 출석 이력',
            onPressed: () {
              Navigator.of(context).push(MaterialPageRoute(builder: (_) => const AttendanceHistoryScreen()));
            },
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: '로그아웃',
            onPressed: _confirmLogout,
          ),
        ],
      ),
      body: _loadingToday
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 20, 24, 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    '지금 출석 체크를\n하시겠어요?',
                    style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: AtmColors.primary, height: 1.3),
                  ),
                  const SizedBox(height: 20),
                  if (canAct)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(16),
                      decoration: BoxDecoration(
                        border: Border.all(color: AtmColors.border, width: 2),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _InfoRow(icon: Icons.place_outlined, label: '근무 장소', value: _today!.placeName ?? '-'),
                          const SizedBox(height: 16),
                          _InfoRow(
                            icon: Icons.access_time,
                            label: '근무 시간',
                            value: '${_today!.startTime ?? ''} — ${_today!.endTime ?? ''}',
                          ),
                        ],
                      ),
                    )
                  else
                    const Text('오늘은 예정된 출석이 없습니다', style: TextStyle(color: AtmColors.onSurfaceVariant, fontSize: 16)),
                  if (_errorMessage != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 16),
                      child: Text(_errorMessage!, style: const TextStyle(color: AtmColors.error, fontWeight: FontWeight.bold)),
                    ),
                ],
              ),
            ),
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
        child: AtmBottomActionBar.confirm(
          onYes: (canAct && !_checkingIn && !_declining) ? _confirmCheckIn : null,
          onNo: (canAct && !_checkingIn && !_declining) ? _declineCheckIn : null,
        ),
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;

  const _InfoRow({required this.icon, required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 22, color: AtmColors.primary),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(label, style: const TextStyle(fontSize: 14, color: AtmColors.onSurfaceVariant)),
              const SizedBox(height: 2),
              Text(value, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AtmColors.primary)),
            ],
          ),
        ),
      ],
    );
  }
}
