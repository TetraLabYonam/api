import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/unit_type.dart';
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
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _queryController,
                    decoration: const InputDecoration(labelText: '청소, 화단, 쓰레기 줍기 등'),
                  ),
                ),
                IconButton(icon: const Icon(Icons.search), onPressed: _loading ? null : _search),
              ],
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          if (_loading) const Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator()),
          if (!_loading && _searchedOnce && _results.isEmpty)
            Padding(
              padding: const EdgeInsets.all(16),
              child: ElevatedButton(
                onPressed: _searchWithAi,
                child: const Text('AI로 더 찾아보기'),
              ),
            ),
          Expanded(
            child: ListView.builder(
              itemCount: _results.length,
              itemBuilder: (context, index) {
                final place = _results[index];
                return ListTile(
                  title: Text(place.name),
                  subtitle: Text(place.address),
                  onTap: _loading ? null : () => _select(place),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
