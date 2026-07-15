import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../auth/auth_provider.dart';
import '../checkin/checkin_screen.dart';
import 'consent_repository.dart';

class ConsentScreen extends ConsumerStatefulWidget {
  const ConsentScreen({super.key});

  @override
  ConsumerState<ConsentScreen> createState() => _ConsentScreenState();
}

class _ConsentScreenState extends ConsumerState<ConsentScreen> {
  bool _loading = false;
  String? _error;

  static const _fullTerms =
      '출석 체크를 위해 체크인 시점의 위치정보(GPS 좌표)를 수집합니다. '
      '수집된 위치정보는 출석 확인 목적으로만 사용되며, 최초 1회 동의로 이후 출석 체크에 계속 적용됩니다.';

  Future<void> _agree() async {
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

  void _decline() {
    Navigator.of(context).maybePop();
  }

  void _showFullTerms() {
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('위치정보 수집 약관'),
        content: const SingleChildScrollView(child: Text(_fullTerms)),
        actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('닫기'))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('위치정보 수집 동의')),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 24, 20, 12),
            child: Text('위치정보 수집에\n동의하시겠어요?',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.black)),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 20),
            child: Text(
              '출석 체크 시점의 위치(GPS)를 수집하며, 출석 확인 목적으로만 사용됩니다. 최초 1회만 동의하면 됩니다.',
              style: TextStyle(fontSize: 15, color: Colors.black54),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
            child: GestureDetector(
              onTap: _showFullTerms,
              child: const Text('자세히 보기',
                  style: TextStyle(
                      fontSize: 14,
                      color: AtmColors.primary,
                      decoration: TextDecoration.underline,
                      fontWeight: FontWeight.bold)),
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          if (_loading) const Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator()),
        ],
      ),
      bottomNavigationBar: AtmBottomActionBar.confirm(
        onYes: _loading ? null : _agree,
        onNo: _decline,
      ),
    );
  }
}
