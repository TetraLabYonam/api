import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../../design_system/atm_numeric_keypad.dart';
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
      appBar: AppBar(title: const Text('로그인')),
      body: Column(
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 24, 20, 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('전화번호를\n입력해주세요',
                  style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: Colors.black)),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(border: Border.all(color: AtmColors.primary, width: 2)),
              child: Text(_phoneNumber, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: AtmNumericKeypad(
              onDigit: _onDigit,
              onBackspace: _onBackspace,
              onConfirm: (_sending || _phoneNumber.isEmpty) ? null : _sendOtp,
              confirmLabel: _sending ? '전송 중...' : '인증받기',
            ),
          ),
          const Spacer(),
          AtmBottomActionBar.single(label: '취소', onPressed: () => setState(() => _phoneNumber = '')),
        ],
      ),
    );
  }
}
