import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../../design_system/atm_numeric_keypad.dart';
import 'auth_provider.dart';

class OtpVerifyScreen extends ConsumerStatefulWidget {
  final String phoneNumber;

  const OtpVerifyScreen({super.key, required this.phoneNumber});

  @override
  ConsumerState<OtpVerifyScreen> createState() => _OtpVerifyScreenState();
}

class _OtpVerifyScreenState extends ConsumerState<OtpVerifyScreen> {
  String _code = '';
  String? _error;

  void _onDigit(String digit) {
    if (_code.length >= 6) return;
    setState(() => _code += digit);
  }

  void _onBackspace() {
    if (_code.isEmpty) return;
    setState(() => _code = _code.substring(0, _code.length - 1));
  }

  Future<void> _verify() async {
    try {
      await ref.read(authRepositoryProvider).verifyOtp(widget.phoneNumber, _code);
      if (!mounted) return;
      Navigator.of(context).pushNamedAndRemoveUntil('/unit-selection', (route) => false);
    } catch (e) {
      setState(() => _error = '인증번호가 올바르지 않습니다.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('ATTENDANCE')),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('인증번호 6자리를\n입력해주세요',
                        style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: AtmColors.primary, height: 1.3)),
                    const SizedBox(height: 20),
                    Row(
                      children: List.generate(6, (i) {
                        final filled = i < _code.length;
                        return Expanded(
                          child: Container(
                            margin: const EdgeInsets.symmetric(horizontal: 4),
                            padding: const EdgeInsets.symmetric(vertical: 14),
                            decoration: BoxDecoration(
                              border: Border.all(color: AtmColors.border, width: 2),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            alignment: Alignment.center,
                            child: Text(filled ? _code[i] : '',
                                style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AtmColors.primary)),
                          ),
                        );
                      }),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 12),
                      Text(_error!, style: const TextStyle(color: AtmColors.error, fontSize: 16, fontWeight: FontWeight.bold)),
                    ],
                    const SizedBox(height: 20),
                    AtmNumericKeypad(
                      onDigit: _onDigit,
                      onBackspace: _onBackspace,
                      onConfirm: _code.length == 6 ? _verify : null,
                      confirmLabel: '확인',
                    ),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 20),
              child: AtmBottomActionBar.single(label: '이전', onPressed: () => Navigator.of(context).maybePop()),
            ),
          ],
        ),
      ),
    );
  }
}
