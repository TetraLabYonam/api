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

  Future<void> _loadAll() async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    final results = await repo.list(widget.unitType);
    setState(() => _results = results);
  }

  Future<void> _search() async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    final results = await repo.search(widget.unitType, _queryController.text);
    setState(() {
      _results = results;
      _searchedOnce = true;
    });
  }

  Future<void> _searchWithAi() async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    final results = await repo.searchFallback(widget.unitType, _queryController.text);
    setState(() => _results = results);
  }

  Future<void> _select(PlaceSummary place) async {
    final repo = JobRepository(dio: ref.read(apiClientProvider).dio);
    await repo.assignPlace(place.id);
    if (!mounted) return;
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ConsentScreen()));
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
                IconButton(icon: const Icon(Icons.search), onPressed: _search),
              ],
            ),
          ),
          if (_searchedOnce && _results.isEmpty)
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
                  onTap: () => _select(place),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
