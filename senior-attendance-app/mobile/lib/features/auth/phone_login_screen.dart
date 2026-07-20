import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_colors.dart';
import '../../design_system/atm_numeric_keypad.dart';
import '../../design_system/atm_secondary_button.dart';
import 'auth_provider.dart';
import 'otp_verify_screen.dart';

class PhoneLoginScreen extends ConsumerStatefulWidget {
  const PhoneLoginScreen({super.key});

  @override
  ConsumerState<PhoneLoginScreen> createState() => _PhoneLoginScreenState();
}

class _PhoneLoginScreenState extends ConsumerState<PhoneLoginScreen> {
  String _phoneNumber = '';
  bool _sending = false;

  void _onDigit(String digit) {
    if (_phoneNumber.length >= 11) return;
    setState(() => _phoneNumber += digit);
  }

  void _onBackspace() {
    if (_phoneNumber.isEmpty) return;
    setState(() => _phoneNumber = _phoneNumber.substring(0, _phoneNumber.length - 1));
  }

  Future<void> _sendOtp() async {
    setState(() => _sending = true);
    try {
      await ref.read(authRepositoryProvider).requestOtp(_phoneNumber);
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => OtpVerifyScreen(phoneNumber: _phoneNumber),
      ));
    } finally {
      if (mounted) setState(() => _sending = false);
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
                    const Text('본인 확인을 위해\n전화번호를 입력해주세요',
                        style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: AtmColors.primary, height: 1.3)),
                    const SizedBox(height: 20),
                    const Text('전화번호',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AtmColors.primary)),
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      constraints: const BoxConstraints(minHeight: 64),
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      decoration: BoxDecoration(
                        border: Border.all(color: AtmColors.border, width: 2),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      alignment: Alignment.centerLeft,
                      child: Text(_phoneNumber,
                          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AtmColors.primary)),
                    ),
                    const SizedBox(height: 20),
                    AtmNumericKeypad(
                      onDigit: _onDigit,
                      onBackspace: _onBackspace,
                      onConfirm: (_sending || _phoneNumber.isEmpty) ? null : _sendOtp,
                      confirmLabel: _sending ? '전송 중...' : '인증받기',
                    ),
                  ],
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 20),
              child: AtmSecondaryButton(
                label: '취소',
                onPressed: () => setState(() => _phoneNumber = ''),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
