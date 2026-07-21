import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_colors.dart';
import '../../design_system/atm_primary_button.dart';
import '../../design_system/atm_secondary_button.dart';
import '../consent/consent_screen.dart';
import 'auth_provider.dart';
import 'qr_login_screen.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _employeeIdController = TextEditingController();
  final _phoneNumberController = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _employeeIdController.dispose();
    _phoneNumberController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final employeeId = int.tryParse(_employeeIdController.text);
    if (employeeId == null || _phoneNumberController.text.isEmpty) {
      setState(() => _error = '직번 또는 전화번호를 확인해주세요.');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ref.read(authRepositoryProvider).login(employeeId, _phoneNumberController.text);
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const ConsentScreen()),
        (route) => false,
      );
    } on DioException catch (e) {
      if (!mounted) return;
      final message = e.response?.data is Map
          ? (e.response?.data['error'] as String? ?? '직번 또는 전화번호를 확인해주세요.')
          : '직번 또는 전화번호를 확인해주세요.';
      setState(() => _error = message);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '직번 또는 전화번호를 확인해주세요.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  void _loginWithQr() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const QrLoginScreen()));
  }

  InputDecoration _fieldDecoration(String hintText) {
    return InputDecoration(
      hintText: hintText,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: AtmColors.border, width: 2),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(8),
        borderSide: const BorderSide(color: AtmColors.border, width: 2),
      ),
    );
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
                    const Text('직번과 전화번호를\n입력해주세요',
                        style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: AtmColors.primary, height: 1.3)),
                    const SizedBox(height: 20),
                    const Text('직번',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AtmColors.primary)),
                    const SizedBox(height: 8),
                    TextField(
                      key: const Key('employeeIdField'),
                      controller: _employeeIdController,
                      keyboardType: TextInputType.number,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AtmColors.primary),
                      decoration: _fieldDecoration('직번을 입력해주세요'),
                    ),
                    const SizedBox(height: 20),
                    const Text('전화번호',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AtmColors.primary)),
                    const SizedBox(height: 8),
                    TextField(
                      key: const Key('phoneNumberField'),
                      controller: _phoneNumberController,
                      keyboardType: TextInputType.phone,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AtmColors.primary),
                      decoration: _fieldDecoration('전화번호를 입력해주세요'),
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 16),
                      Text(_error!, style: const TextStyle(color: AtmColors.error, fontSize: 16, fontWeight: FontWeight.bold)),
                    ],
                    const SizedBox(height: 24),
                    AtmPrimaryButton(
                      label: _submitting ? '로그인 중...' : '로그인',
                      onPressed: _submitting ? null : _login,
                    ),
                    const SizedBox(height: 12),
                    AtmSecondaryButton(
                      label: 'QR로 로그인',
                      onPressed: _loginWithQr,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
