import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../auth/auth_provider.dart';
import 'checkin_repository.dart';

class CheckinScreen extends ConsumerStatefulWidget {
  const CheckinScreen({super.key});

  @override
  ConsumerState<CheckinScreen> createState() => _CheckinScreenState();
}

class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  String? _resultMessage;
  bool _loading = false;

  Future<void> _checkIn(int scheduleId) async {
    setState(() => _loading = true);
    try {
      final permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        if (!mounted) return;
        setState(() => _resultMessage = '위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.');
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
      setState(() => _resultMessage = result.message);
    } catch (e) {
      if (mounted) {
        setState(() => _resultMessage = '위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.');
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('출석 체크')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            if (_resultMessage != null)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(_resultMessage!, textAlign: TextAlign.center),
              ),
            ElevatedButton(
              onPressed: _loading ? null : () => _checkIn(1),
              child: Text(_loading ? '확인 중...' : '출석 체크'),
            ),
          ],
        ),
      ),
    );
  }
}
