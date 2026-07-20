import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/unit_type.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../../design_system/atm_option_list_item.dart';
import '../../design_system/atm_secondary_button.dart';
import '../auth/auth_provider.dart';
import '../consent/consent_screen.dart';
import 'job_repository.dart';

class JobSearchScreen extends ConsumerStatefulWidget {
  final UnitType unitType;

  const JobSearchScreen({super.key, required this.unitType});

  @override
  ConsumerState<JobSearchScreen> createState() => _JobSearchScreenState();
}

class _JobSearchScreenState extends ConsumerState<JobSearchScreen> {
  final _queryController = TextEditingController();
  List<PlaceSummary> _results = [];
  bool _searchedOnce = false;
  bool _loading = false;
  String? _error;

  Future<void> _loadAll() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
      final results = await repo.list(widget.unitType);
      if (!mounted) return;
      setState(() => _results = results);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '일자리 목록을 불러오지 못했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
      final results = await repo.search(widget.unitType, _queryController.text);
      if (!mounted) return;
      setState(() {
        _results = results;
        _searchedOnce = true;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '검색에 실패했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _searchWithAi() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
      final results = await repo.searchFallback(widget.unitType, _queryController.text);
      if (!mounted) return;
      setState(() => _results = results);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'AI 검색에 실패했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _select(PlaceSummary place) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
      await repo.assignPlace(place.id);
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ConsentScreen()));
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '일자리 등록에 실패했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void initState() {
    super.initState();
    _loadAll();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('${widget.unitType.label} 일자리 찾기')),
      body: Column(
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(24, 20, 24, 12),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('어떤 일을 하시나요?',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AtmColors.primary)),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: TextField(
                    controller: _queryController,
                    style: const TextStyle(fontSize: 18),
                    decoration: InputDecoration(
                      labelText: '청소, 화단, 쓰레기 줍기 등',
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: const BorderSide(color: AtmColors.border, width: 2),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: const BorderSide(color: AtmColors.border, width: 2),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(
                  height: 64,
                  width: 64,
                  child: OutlinedButton(
                    onPressed: _loading ? null : _search,
                    style: OutlinedButton.styleFrom(
                      backgroundColor: AtmColors.primary,
                      side: const BorderSide(color: AtmColors.border, width: 2),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      padding: EdgeInsets.zero,
                    ),
                    child: const Icon(Icons.search, color: AtmColors.onPrimary),
                  ),
                ),
              ],
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 0),
              child: Text(_error!, style: const TextStyle(color: AtmColors.error, fontWeight: FontWeight.bold)),
            ),
          if (_loading) const Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator()),
          if (!_loading && _searchedOnce && _results.isEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
              child: AtmSecondaryButton(label: 'AI로 더 찾아보기', onPressed: _searchWithAi),
            ),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: _results.length,
              itemBuilder: (context, index) {
                final place = _results[index];
                return AtmOptionListItem(
                  title: place.name,
                  subtitle: place.address,
                  onTap: _loading ? null : () => _select(place),
                );
              },
            ),
          ),
        ],
      ),
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
        child: AtmBottomActionBar.single(
          label: '이전',
          onPressed: () => Navigator.of(context).maybePop(),
        ),
      ),
    );
  }
}
