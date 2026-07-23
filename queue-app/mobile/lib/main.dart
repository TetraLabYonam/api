import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

const _navy = Color(0xff001736);
const _teal = Color(0xff2a6767);
const _background = Color(0xfff9f9f9);
const _outline = Color(0xff747780);

void main() => runApp(const UnifiedApp());

enum AppDomain { chooser, member, admin }

const _compileTimeDomain = String.fromEnvironment('DOMAIN_TOKEN');

AppDomain domainFromToken(String token) => switch (token) {
  'member' => AppDomain.member,
  'admin' => AppDomain.admin,
  _ => AppDomain.chooser,
};

class UnifiedApp extends StatefulWidget {
  const UnifiedApp({
    super.key,
    this.domainToken,
    this.repository,
    this.adminApi,
  });

  final String? domainToken;
  final JobRepository? repository;
  final AdminApi? adminApi;

  @override
  State<UnifiedApp> createState() => _UnifiedAppState();
}

class _UnifiedAppState extends State<UnifiedApp> {
  late AppDomain _domain = domainFromToken(
    widget.domainToken ?? _compileTimeDomain,
  );

  void _selectDomain(AppDomain domain) => setState(() => _domain = domain);

  @override
  void didUpdateWidget(covariant UnifiedApp oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.domainToken != widget.domainToken) {
      _domain = domainFromToken(widget.domainToken ?? _compileTimeDomain);
    }
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    debugShowCheckedModeBanner: false,
    title: '순번표 접수 운영',
    theme: ThemeData(
      useMaterial3: true,
      scaffoldBackgroundColor: _background,
      colorScheme: const ColorScheme.light(
        primary: _navy,
        secondary: _teal,
        surface: Colors.white,
      ),
      fontFamily: 'Noto Sans KR',
      textTheme: const TextTheme(
        bodyLarge: TextStyle(fontSize: 18, color: Color(0xff1a1c1c)),
      ),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(),
        enabledBorder: OutlineInputBorder(
          borderSide: BorderSide(color: _outline),
        ),
      ),
    ),
    home: switch (_domain) {
      AppDomain.chooser => RoleSelectionScreen(onSelectDomain: _selectDomain),
      AppDomain.member => JobListScreen(
        repository: widget.repository ?? HttpJobRepository(),
        onSelectRole: () => _selectDomain(AppDomain.chooser),
      ),
      AppDomain.admin => LoginScreen(
        api: widget.adminApi ?? HttpAdminApi(),
        onSelectRole: () => _selectDomain(AppDomain.chooser),
      ),
    },
  );
}

class MemberApp extends StatelessWidget {
  const MemberApp({super.key, this.repository});

  final JobRepository? repository;

  @override
  Widget build(BuildContext context) =>
      UnifiedApp(domainToken: 'member', repository: repository);
}

class AdminApp extends StatelessWidget {
  const AdminApp({super.key, this.api});

  final AdminApi? api;

  @override
  Widget build(BuildContext context) =>
      UnifiedApp(domainToken: 'admin', adminApi: api);
}

class RoleSelectionScreen extends StatelessWidget {
  const RoleSelectionScreen({super.key, required this.onSelectDomain});

  final ValueChanged<AppDomain> onSelectDomain;

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(
                  Icons.confirmation_number_outlined,
                  size: 56,
                  color: _teal,
                ),
                const SizedBox(height: 20),
                Text(
                  '순번표 접수',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: _navy,
                  ),
                ),
                const SizedBox(height: 8),
                const Text('이용할 서비스를 선택하세요.', textAlign: TextAlign.center),
                const SizedBox(height: 28),
                FilledButton.icon(
                  onPressed: () => onSelectDomain(AppDomain.member),
                  icon: const Icon(Icons.person_outline),
                  label: const Text('이용자'),
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                  ),
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => onSelectDomain(AppDomain.admin),
                  icon: const Icon(Icons.admin_panel_settings_outlined),
                  label: const Text('관리자'),
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}

class MemberJob {
  const MemberJob({
    required this.id,
    required this.title,
    required this.unitName,
    required this.sessionUid,
  });

  final int id;
  final String title;
  final String unitName;
  final String sessionUid;

  factory MemberJob.fromJson(Map<String, dynamic> json) => MemberJob(
    id: json['id'] as int,
    title: json['title'] as String,
    unitName: json['unitName'] as String,
    sessionUid: json['sessionUid'] as String,
  );
}

class TicketResult {
  const TicketResult({required this.number, required this.duplicate});

  final int number;
  final bool duplicate;

  factory TicketResult.fromJson(Map<String, dynamic> json) => TicketResult(
    number: json['number'] as int,
    duplicate: json['duplicate'] as bool,
  );
}

abstract class JobRepository {
  Future<List<MemberJob>> openJobs();
  Future<TicketResult> issueTicket(MemberJob job, String phone);
}

class ApiException implements Exception {
  const ApiException(this.status, this.code);

  final int status;
  final String? code;
}

class HttpJobRepository implements JobRepository {
  HttpJobRepository({http.Client? client, String? baseUrl})
    : _client = client ?? http.Client(),
      _baseUrl =
          baseUrl ??
          const String.fromEnvironment(
            'API_BASE_URL',
            defaultValue: 'http://10.0.2.2:8080',
          );

  final http.Client _client;
  final String _baseUrl;

  @override
  Future<List<MemberJob>> openJobs() async {
    final response = await _client.get(Uri.parse('$_baseUrl/api/v1/jobs'));
    if (response.statusCode != 200) throw _exception(response);
    final data = jsonDecode(response.body) as List<dynamic>;
    return data
        .map((item) => MemberJob.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<TicketResult> issueTicket(MemberJob job, String phone) async {
    final response = await _client.post(
      Uri.parse(
        '$_baseUrl/api/v1/jobs/${job.id}/ticket-sessions/${job.sessionUid}/tickets',
      ),
      headers: const {'Content-Type': 'application/json'},
      body: jsonEncode({'phone': phone}),
    );
    if (response.statusCode != 200) throw _exception(response);
    return TicketResult.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  ApiException _exception(http.Response response) {
    String? code;
    try {
      code =
          (jsonDecode(response.body) as Map<String, dynamic>)['code']
              as String?;
    } catch (_) {}
    return ApiException(response.statusCode, code);
  }
}

class JobListScreen extends StatefulWidget {
  const JobListScreen({super.key, required this.repository, this.onSelectRole});
  final JobRepository repository;
  final VoidCallback? onSelectRole;

  @override
  State<JobListScreen> createState() => _JobListScreenState();
}

class _JobListScreenState extends State<JobListScreen> {
  late Future<List<MemberJob>> _jobs = widget.repository.openJobs();
  String? _unit;
  MemberJob? _selected;

  void _reload() => setState(() {
    _jobs = widget.repository.openJobs();
    _selected = null;
  });

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text(
        '순번표 발급',
        style: TextStyle(fontWeight: FontWeight.w700),
      ),
      backgroundColor: _background,
      actions: [
        if (widget.onSelectRole != null)
          TextButton(
            onPressed: widget.onSelectRole,
            child: const Text('역할 선택'),
          ),
      ],
    ),
    body: FutureBuilder<List<MemberJob>>(
      future: _jobs,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return _Recovery(message: '일자리 목록을 불러오지 못했습니다.', onRetry: _reload);
        }
        final jobs = snapshot.data!;
        final units = jobs.map((job) => job.unitName).toSet().toList()..sort();
        final visible = _unit == null
            ? jobs
            : jobs.where((job) => job.unitName == _unit).toList();
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 8),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  '원하시는 일자리를 선택하세요',
                  style: Theme.of(
                    context,
                  ).textTheme.bodyLarge!.copyWith(fontWeight: FontWeight.w700),
                ),
              ),
            ),
            SizedBox(
              height: 52,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16),
                children: [
                  _UnitChip(
                    label: '전체',
                    selected: _unit == null,
                    onTap: () => setState(() => _unit = null),
                  ),
                  ...units.map(
                    (unit) => _UnitChip(
                      label: unit,
                      selected: _unit == unit,
                      onTap: () => setState(() => _unit = unit),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: visible.isEmpty
                  ? const Center(child: Text('선택한 사업단의 열린 일자리가 없습니다.'))
                  : ListView.builder(
                      padding: const EdgeInsets.fromLTRB(20, 12, 20, 100),
                      itemCount: visible.length,
                      itemBuilder: (context, index) {
                        final job = visible[index];
                        return _JobCard(
                          job: job,
                          selected: _selected?.id == job.id,
                          onTap: () => setState(() => _selected = job),
                        );
                      },
                    ),
            ),
          ],
        );
      },
    ),
    bottomNavigationBar: SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 12),
            child: SizedBox(
              height: 64,
              width: double.infinity,
              child: FilledButton(
                onPressed: _selected == null
                    ? null
                    : () => Navigator.of(context).push(
                        MaterialPageRoute(
                          builder: (_) => ConfirmScreen(
                            repository: widget.repository,
                            job: _selected!,
                          ),
                        ),
                      ),
                child: const Text(
                  '선택한 일자리로 순번표 받기',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ),
          const _BottomNavigation(),
        ],
      ),
    ),
  );
}

class _UnitChip extends StatelessWidget {
  const _UnitChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });
  final String label;
  final bool selected;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(right: 8),
    child: ChoiceChip(
      label: Text(label, style: const TextStyle(fontSize: 18)),
      selected: selected,
      onSelected: (_) => onTap(),
      selectedColor: const Color(0xffaeebea),
      side: const BorderSide(color: _outline),
    ),
  );
}

class _JobCard extends StatelessWidget {
  const _JobCard({
    required this.job,
    required this.selected,
    required this.onTap,
  });
  final MemberJob job;
  final bool selected;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Semantics(
    selected: selected,
    button: true,
    label: '${job.unitName} ${job.title}',
    child: Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Material(
        color: selected ? const Color(0xffd6e3ff) : Colors.white,
        borderRadius: BorderRadius.circular(18),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(18),
          child: Container(
            constraints: const BoxConstraints(minHeight: 96),
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              border: Border.all(
                color: selected ? _navy : _outline,
                width: selected ? 2 : 1,
              ),
              borderRadius: BorderRadius.circular(18),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  job.unitName,
                  style: const TextStyle(
                    fontSize: 18,
                    color: _teal,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  job.title,
                  style: const TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}

class ConfirmScreen extends StatelessWidget {
  const ConfirmScreen({super.key, required this.repository, required this.job});
  final JobRepository repository;
  final MemberJob job;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('선택 확인')),
    body: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('선택한 일자리', style: TextStyle(fontSize: 18, color: _teal)),
          const SizedBox(height: 12),
          Text(
            job.title,
            style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          Text(job.unitName, style: const TextStyle(fontSize: 18)),
          const Spacer(),
          SizedBox(
            height: 64,
            child: FilledButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => PhoneScreen(repository: repository, job: job),
                ),
              ),
              child: const Text(
                '전화번호 입력하기',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class PhoneScreen extends StatefulWidget {
  const PhoneScreen({super.key, required this.repository, required this.job});
  final JobRepository repository;
  final MemberJob job;
  @override
  State<PhoneScreen> createState() => _PhoneScreenState();
}

class _PhoneScreenState extends State<PhoneScreen> {
  final _controller = TextEditingController();
  bool _loading = false;
  String? _error;
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _issue() async {
    final phone = _controller.text.replaceAll(RegExp(r'[\s-]'), '');
    if (!RegExp(r'^\+82(10|[2-9][0-9])\d{7,8}$').hasMatch(phone)) {
      setState(() => _error = '대한민국 국가번호(+82)를 포함한 전화번호를 입력하세요.');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final ticket = await widget.repository.issueTicket(widget.job, phone);
      if (!mounted) return;
      _controller.clear();
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => SuccessScreen(ticket: ticket, job: widget.job),
        ),
      );
    } on ApiException catch (e) {
      if (mounted) {
        setState(
          () => _error = e.status == 404 || e.status == 409
              ? '일자리 또는 접수가 마감되었습니다. 목록에서 다시 선택하세요.'
              : e.status == 400
              ? '전화번호를 다시 확인하세요.'
              : '발급 중 문제가 생겼습니다. 다시 시도하세요.',
        );
      }
    } catch (_) {
      if (mounted) setState(() => _error = '네트워크 연결을 확인한 후 다시 시도하세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('전화번호 입력')),
    body: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            '순번표 발급을 위해 전화번호를 입력하세요.',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 12),
          const Text('예: +821012345678', style: TextStyle(fontSize: 18)),
          const SizedBox(height: 24),
          TextField(
            controller: _controller,
            keyboardType: TextInputType.phone,
            autofocus: true,
            style: const TextStyle(fontSize: 20),
            decoration: InputDecoration(
              errorText: _error,
              labelText: '전화번호',
              labelStyle: const TextStyle(fontSize: 18),
              border: const OutlineInputBorder(),
            ),
          ),
          const Spacer(),
          SizedBox(
            height: 64,
            child: FilledButton(
              onPressed: _loading ? null : _issue,
              child: _loading
                  ? const CircularProgressIndicator(color: Colors.white)
                  : const Text(
                      '순번표 발급받기',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
            ),
          ),
        ],
      ),
    ),
  );
}

class SuccessScreen extends StatelessWidget {
  const SuccessScreen({super.key, required this.ticket, required this.job});
  final TicketResult ticket;
  final MemberJob job;
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Spacer(),
            Icon(Icons.confirmation_number_outlined, size: 72, color: _teal),
            const SizedBox(height: 24),
            Text(
              ticket.duplicate ? '이미 발급된 순번표입니다' : '순번표가 발급되었습니다',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            Text(
              ticket.duplicate ? '기존에 발급받은 번호를 보여드립니다.' : '공용 순번입니다.',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 18),
            ),
            const SizedBox(height: 20),
            Text(
              '${ticket.number}',
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontSize: 88,
                height: 1,
                fontWeight: FontWeight.w800,
                color: _navy,
              ),
            ),
            const SizedBox(height: 24),
            Text(
              '${job.unitName}\n${job.title}',
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 18),
            ),
            const Spacer(),
            SizedBox(
              height: 64,
              child: OutlinedButton(
                onPressed: () =>
                    Navigator.of(context).popUntil((route) => route.isFirst),
                child: const Text(
                  '다른 일자리 선택',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _Recovery extends StatelessWidget {
  const _Recovery({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            message,
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 18),
          ),
          const SizedBox(height: 20),
          SizedBox(
            height: 52,
            child: OutlinedButton(
              onPressed: onRetry,
              child: const Text('다시 시도', style: TextStyle(fontSize: 18)),
            ),
          ),
        ],
      ),
    ),
  );
}

class _BottomNavigation extends StatelessWidget {
  const _BottomNavigation();
  @override
  Widget build(BuildContext context) => const SizedBox(
    height: 64,
    child: Row(
      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
      children: [
        Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.confirmation_number, color: _navy),
            Text('순번표', style: TextStyle(fontSize: 14)),
          ],
        ),
        Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.help_outline),
            Text('안내', style: TextStyle(fontSize: 14)),
          ],
        ),
      ],
    ),
  );
}

class AdminJob {
  const AdminJob({
    required this.id,
    required this.title,
    required this.unitName,
    this.sessionUid,
  });

  final int id;
  final String title;
  final String unitName;
  final String? sessionUid;

  factory AdminJob.fromJson(Map<String, dynamic> json) => AdminJob(
    id: json['id'] as int,
    title: json['title'] as String,
    unitName: json['unitName'] as String,
    sessionUid: json['sessionUid'] as String?,
  );
}

abstract class AdminApi {
  Future<List<AdminJob>> jobs();
  Future<AdminJob> createJob(String title, String unitName);
  Future<void> openSession(int jobId);
  Future<void> closeSession(String sessionUid);
  void setToken(String token);
}

class HttpAdminApi implements AdminApi {
  HttpAdminApi({http.Client? client, String? baseUrl})
    : _client = client ?? http.Client(),
      _baseUrl =
          baseUrl ??
          const String.fromEnvironment(
            'API_BASE_URL',
            defaultValue: 'http://10.0.2.2:8080',
          );

  final http.Client _client;
  final String _baseUrl;
  String? _token;

  @override
  void setToken(String token) => _token = token;

  Map<String, String> get _headers => {
    'Content-Type': 'application/json',
    if (_token != null) 'Authorization': 'Bearer $_token',
  };

  Future<dynamic> _request(Future<http.Response> request) async {
    final response = await request;
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('요청을 처리하지 못했습니다. (${response.statusCode})');
    }
    return response.body.isEmpty ? null : jsonDecode(response.body);
  }

  @override
  Future<List<AdminJob>> jobs() async {
    final body = await _request(
      _client.get(Uri.parse('$_baseUrl/api/v1/admin/jobs'), headers: _headers),
    );
    return (body as List)
        .map((value) => AdminJob.fromJson(value as Map<String, dynamic>))
        .toList();
  }

  @override
  Future<AdminJob> createJob(String title, String unitName) async =>
      AdminJob.fromJson(
        await _request(
              _client.post(
                Uri.parse('$_baseUrl/api/v1/admin/jobs'),
                headers: _headers,
                body: jsonEncode({'title': title, 'unitName': unitName}),
              ),
            )
            as Map<String, dynamic>,
      );

  @override
  Future<void> openSession(int jobId) async {
    await _request(
      _client.post(
        Uri.parse('$_baseUrl/api/v1/admin/jobs/$jobId/ticket-sessions'),
        headers: _headers,
      ),
    );
  }

  @override
  Future<void> closeSession(String sessionUid) async {
    await _request(
      _client.post(
        Uri.parse('$_baseUrl/api/v1/admin/ticket-sessions/$sessionUid/close'),
        headers: _headers,
      ),
    );
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.api, this.onSelectRole});
  final AdminApi api;
  final VoidCallback? onSelectRole;
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _token = TextEditingController();
  String? _error;
  @override
  void dispose() {
    _token.dispose();
    super.dispose();
  }

  void _login() {
    if (_token.text.trim().isEmpty) {
      setState(() => _error = '운영 토큰을 입력하세요.');
      return;
    }
    widget.api.setToken(_token.text.trim());
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (_) =>
            AdminJobsScreen(api: widget.api, onSelectRole: widget.onSelectRole),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 440),
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Icon(
                  Icons.admin_panel_settings_outlined,
                  size: 48,
                  color: _teal,
                ),
                const SizedBox(height: 20),
                Text(
                  '접수 운영',
                  style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: _navy,
                  ),
                ),
                const SizedBox(height: 8),
                const Text('관리자 bootstrap 토큰으로 운영을 시작합니다.'),
                const SizedBox(height: 28),
                TextField(
                  controller: _token,
                  obscureText: true,
                  autocorrect: false,
                  enableSuggestions: false,
                  decoration: InputDecoration(
                    labelText: '운영 토큰',
                    errorText: _error,
                  ),
                ),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: _login,
                  style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                  ),
                  child: const Text('운영 시작'),
                ),
                if (widget.onSelectRole != null)
                  TextButton(
                    onPressed: widget.onSelectRole,
                    child: const Text('역할 선택으로 돌아가기'),
                  ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}

class AdminJobsScreen extends StatefulWidget {
  const AdminJobsScreen({super.key, required this.api, this.onSelectRole});
  final AdminApi api;
  final VoidCallback? onSelectRole;
  @override
  State<AdminJobsScreen> createState() => _AdminJobsScreenState();
}

class _AdminJobsScreenState extends State<AdminJobsScreen> {
  late Future<List<AdminJob>> _jobs;
  @override
  void initState() {
    super.initState();
    _jobs = widget.api.jobs();
  }

  void _refresh() {
    setState(() {
      _jobs = widget.api.jobs();
    });
  }

  Future<void> _mutate(Future<void> Function() action) async {
    try {
      await action();
      if (mounted) {
        _refresh();
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('작업을 처리하지 못했습니다: $error')));
      }
    }
  }

  Future<void> _create() async {
    final title = TextEditingController();
    final unit = TextEditingController();
    String? error;
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('일자리 만들기'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: title,
                decoration: const InputDecoration(labelText: '일자리명'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: unit,
                decoration: InputDecoration(
                  labelText: '사업단명',
                  errorText: error,
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: const Text('취소'),
            ),
            FilledButton(
              onPressed: () async {
                if (title.text.trim().isEmpty || unit.text.trim().isEmpty) {
                  setDialogState(() => error = '일자리명과 사업단명을 입력하세요.');
                  return;
                }
                try {
                  await widget.api.createJob(
                    title.text.trim(),
                    unit.text.trim(),
                  );
                } catch (caught) {
                  if (dialogContext.mounted) {
                    setDialogState(() => error = '일자리를 만들지 못했습니다: $caught');
                  }
                  return;
                }
                if (mounted) {
                  _refresh();
                }
                if (dialogContext.mounted) Navigator.pop(dialogContext);
              },
              child: const Text('만들기'),
            ),
          ],
        ),
      ),
    );
    title.dispose();
    unit.dispose();
  }

  Future<void> _close(AdminJob job) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('접수 마감'),
        content: Text('${job.title} 접수를 마감할까요?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('취소'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('마감'),
          ),
        ],
      ),
    );
    if (confirmed == true && job.sessionUid != null) {
      await _mutate(() => widget.api.closeSession(job.sessionUid!));
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('접수 운영'),
      actions: [
        if (widget.onSelectRole != null)
          IconButton(
            onPressed: widget.onSelectRole,
            tooltip: '역할 선택',
            icon: const Icon(Icons.switch_account_outlined),
          ),
        IconButton(
          onPressed: _refresh,
          tooltip: '새로고침',
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: FutureBuilder<List<AdminJob>>(
      future: _jobs,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.error_outline,
                    color: Color(0xffba1a1a),
                    size: 48,
                  ),
                  const SizedBox(height: 12),
                  const Text('목록을 불러오지 못했습니다.'),
                  TextButton(onPressed: _refresh, child: const Text('다시 시도')),
                ],
              ),
            ),
          );
        }
        final jobs = snapshot.data!;
        if (jobs.isEmpty) {
          return const Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.inbox_outlined, size: 56, color: _teal),
                SizedBox(height: 12),
                Text('운영 중인 일자리가 없습니다.'),
              ],
            ),
          );
        }
        return RefreshIndicator(
          onRefresh: () async => _refresh(),
          child: ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: jobs.length,
            separatorBuilder: (_, _) => const SizedBox(height: 12),
            itemBuilder: (_, index) => _AdminJobCard(
              job: jobs[index],
              onOpen: () =>
                  _mutate(() => widget.api.openSession(jobs[index].id)),
              onClose: () => _close(jobs[index]),
            ),
          ),
        );
      },
    ),
    floatingActionButton: FloatingActionButton.extended(
      onPressed: _create,
      backgroundColor: _teal,
      foregroundColor: Colors.white,
      label: const Text('일자리 만들기'),
      icon: const Icon(Icons.add),
    ),
  );
}

class _AdminJobCard extends StatelessWidget {
  const _AdminJobCard({
    required this.job,
    required this.onOpen,
    required this.onClose,
  });
  final AdminJob job;
  final VoidCallback onOpen;
  final VoidCallback onClose;
  @override
  Widget build(BuildContext context) {
    final open = job.sessionUid != null;
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: _outline),
      ),
      elevation: 0,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    job.title,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                Chip(
                  label: Text(open ? '접수 중' : '대기'),
                  backgroundColor: open
                      ? const Color(0xffaeebea)
                      : const Color(0xffe2e2e2),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(job.unitName),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              height: 56,
              child: open
                  ? OutlinedButton(
                      onPressed: onClose,
                      child: const Text('접수 마감'),
                    )
                  : FilledButton(
                      onPressed: onOpen,
                      style: FilledButton.styleFrom(backgroundColor: _teal),
                      child: const Text('접수 열기'),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
