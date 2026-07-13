import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../auth/auth_provider.dart';
import '../checkin/checkin_screen.dart';
import 'consent_repository.dart';

class ConsentScreen extends ConsumerStatefulWidget {
  const ConsentScreen({super.key});

  @override
  ConsumerState<ConsentScreen> createState() => _ConsentScreenState();
}

class _ConsentScreenState extends ConsumerState<ConsentScreen> {
  bool _agreed = false;
  bool _loading = false;
  String? _error;

  Future<void> _continue() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ConsentRepository(dio: ref.read(apiClientProvider).dio);
      await repo.agree();
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const CheckinScreen()));
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '동의 처리에 실패했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('위치정보 수집 동의')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              '출석 체크를 위해 체크인 시점의 위치정보(GPS 좌표)를 수집합니다. '
              '수집된 위치정보는 출석 확인 목적으로만 사용되며, 최초 1회 동의로 이후 출석 체크에 계속 적용됩니다.',
            ),
            const SizedBox(height: 16),
            CheckboxListTile(
              value: _agreed,
              onChanged: (value) => setState(() => _agreed = value ?? false),
              title: const Text('위치정보 수집 및 이용에 동의합니다.'),
            ),
            const SizedBox(height: 16),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 16),
                child: Text(_error!, style: const TextStyle(color: Colors.red)),
              ),
            if (_loading)
              const Padding(
                padding: EdgeInsets.only(bottom: 16),
                child: CircularProgressIndicator(),
              ),
            ElevatedButton(
              onPressed: (_agreed && !_loading) ? _continue : null,
              child: const Text('동의하고 계속하기'),
            ),
          ],
        ),
      ),
    );
  }
}
