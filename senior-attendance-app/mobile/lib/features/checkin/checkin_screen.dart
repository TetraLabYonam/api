import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../auth/auth_provider.dart';
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

  void _declineCheckIn() {
    Navigator.of(context).maybePop();
  }

  @override
  Widget build(BuildContext context) {
    if (_result != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('출석 체크')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(_result!.success ? Icons.check_circle : Icons.info,
                  color: _result!.success ? AtmColors.primary : Colors.grey, size: 40),
              const SizedBox(height: 10),
              Text(
                _result!.message,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.black),
              ),
            ],
          ),
        ),
        bottomNavigationBar: AtmBottomActionBar.single(
          label: '확인',
          onPressed: () => Navigator.of(context).popUntil((route) => route.isFirst),
        ),
      );
    }

    final canAct = _today != null && _today!.hasSchedule;

    return Scaffold(
      appBar: AppBar(title: const Text('출석 체크')),
      body: _loadingToday
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('지금 출석 체크를\n하시겠어요?',
                      style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.black)),
                  const SizedBox(height: 20),
                  if (canAct)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      decoration: BoxDecoration(border: Border.all(color: Colors.grey.shade300)),
                      child: Text(
                          '오늘 일정: ${_today!.placeName ?? ''} ${_today!.startTime ?? ''}~${_today!.endTime ?? ''}'),
                    )
                  else
                    const Text('오늘은 예정된 출석이 없습니다', style: TextStyle(color: Colors.black54)),
                  if (_errorMessage != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 16),
                      child: Text(_errorMessage!, style: const TextStyle(color: Colors.red)),
                    ),
                ],
              ),
            ),
      bottomNavigationBar: AtmBottomActionBar.confirm(
        onYes: (canAct && !_checkingIn) ? _confirmCheckIn : null,
        onNo: canAct ? _declineCheckIn : null,
      ),
    );
  }
}
