import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:queue_app/main.dart';

class _RecordingClient extends http.BaseClient {
  _RecordingClient(this.handler);
  final Future<http.Response> Function(http.BaseRequest) handler;
  http.BaseRequest? request;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    this.request = request;
    final response = await handler(request);
    return http.StreamedResponse(
      Stream.value(response.bodyBytes),
      response.statusCode,
      headers: response.headers,
      request: request,
    );
  }
}

void main() {
  test('member client sends public discovery and ticket requests', () async {
    final client = _RecordingClient((request) async {
      if (request.url.path == '/api/v1/jobs') {
        return http.Response(
          jsonEncode([
            {
              'id': 7,
              'title': 'Runner',
              'unitName': 'Unit',
              'sessionUid': 's1',
            },
          ]),
          200,
        );
      }
      expect(request.url.path, '/api/v1/jobs/7/ticket-sessions/s1/tickets');
      expect(request.headers['authorization'], isNull);
      expect(jsonDecode((request as http.Request).body), {
        'phone': '+821012345678',
      });
      return http.Response('{"number":12,"duplicate":false}', 200);
    });
    final repository = HttpJobRepository(
      client: client,
      baseUrl: 'http://test',
    );
    final jobs = await repository.openJobs();
    final ticket = await repository.issueTicket(jobs.single, '+821012345678');
    expect(ticket.number, 12);
  });

  test(
    'admin client uses bearer token and reports status without response parsing',
    () async {
      final client = _RecordingClient((request) async {
        expect(request.headers['authorization'], 'Bearer secret');
        return http.Response('not-json', 503);
      });
      final api = HttpAdminApi(client: client, baseUrl: 'http://test')
        ..setToken('secret');
      await expectLater(api.jobs(), throwsA(isA<Exception>()));
    },
  );
}
