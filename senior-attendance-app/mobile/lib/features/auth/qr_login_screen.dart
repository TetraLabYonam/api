import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../../design_system/atm_colors.dart';
import '../../design_system/atm_secondary_button.dart';
import '../consent/consent_screen.dart';
import 'auth_provider.dart';

/// QR 페이로드("{employeeId}:{phoneNumber}")를 파싱한다.
/// 콜론이 없거나 직번 부분이 정수로 읽히지 않으면 null을 반환한다.
({int employeeId, String phoneNumber})? parseQrPayload(String raw) {
  final separatorIndex = raw.indexOf(':');
  if (separatorIndex < 0) return null;

  final employeeId = int.tryParse(raw.substring(0, separatorIndex));
  if (employeeId == null) return null;

  final phoneNumber = raw.substring(separatorIndex + 1);
  return (employeeId: employeeId, phoneNumber: phoneNumber);
}

/// QR 코드를 스캔해 직번:전화번호를 읽어 로그인하는 화면.
class QrLoginScreen extends ConsumerStatefulWidget {
  const QrLoginScreen({super.key});

  @override
  ConsumerState<QrLoginScreen> createState() => _QrLoginScreenState();
}

class _QrLoginScreenState extends ConsumerState<QrLoginScreen> {
  bool _processing = false;
  String? _error;

  /// 로그인 실패로 이어진 마지막 원본 QR 값.
  /// MobileScanner가 같은 QR을 화면에 계속 인식해도, 사용자가 명시적으로 "다시
  /// 시도"를 누르거나 다른 QR을 비추기 전까지는 같은 값으로 재시도하지 않는다.
  String? _lastFailedRawValue;

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_processing) return;
    if (capture.barcodes.isEmpty) return;

    final rawValue = capture.barcodes.first.rawValue;
    if (rawValue == null) return;
    if (rawValue == _lastFailedRawValue) return;

    final payload = parseQrPayload(rawValue);
    if (payload == null) {
      setState(() => _error = 'QR 코드를 인식하지 못했어요. 다시 시도해주세요.');
      return;
    }

    setState(() {
      _processing = true;
      _error = null;
    });

    try {
      await ref.read(authRepositoryProvider).login(payload.employeeId, payload.phoneNumber);
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const ConsentScreen()),
        (route) => false,
      );
    } on DioException catch (e) {
      if (!mounted) return;
      final message = e.response?.data is Map
          ? (e.response?.data['error'] as String? ?? '로그인에 실패했어요. 직번과 전화번호를 확인해주세요.')
          : '로그인에 실패했어요. 직번과 전화번호를 확인해주세요.';
      setState(() {
        _error = message;
        _processing = false;
        _lastFailedRawValue = rawValue;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '로그인에 실패했어요. 직번과 전화번호를 확인해주세요.';
        _processing = false;
        _lastFailedRawValue = rawValue;
      });
    }
  }

  void _retry() {
    setState(() {
      _error = null;
      _lastFailedRawValue = null;
    });
  }

  void _switchToManualLogin() {
    Navigator.of(context).pop();
  }

  Widget _fallbackMessage(String message) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AtmColors.error),
            ),
            const SizedBox(height: 20),
            AtmSecondaryButton(label: '직접 입력으로 전환', onPressed: _switchToManualLogin),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('QR로 로그인')),
      body: SafeArea(
        child: Column(
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(24, 16, 24, 8),
              child: Text(
                'QR 코드를 화면 중앙에 비춰주세요',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AtmColors.primary),
              ),
            ),
            Expanded(
              child: MobileScanner(
                onDetect: _onDetect,
                errorBuilder: (context, error) => _fallbackMessage('카메라를 사용할 수 없어요. 카메라 권한을 확인해주세요.'),
              ),
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
                child: Column(
                  children: [
                    Text(
                      _error!,
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: AtmColors.error, fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                    if (_lastFailedRawValue != null) ...[
                      const SizedBox(height: 12),
                      AtmSecondaryButton(label: '다시 시도', onPressed: _retry),
                    ],
                  ],
                ),
              ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
              child: AtmSecondaryButton(label: '직접 입력으로 전환', onPressed: _switchToManualLogin),
            ),
          ],
        ),
      ),
    );
  }
}
