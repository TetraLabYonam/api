import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/core/token_storage.dart';

class FakeSecureStore implements SecureStore {
  final Map<String, String> _store = {};

  @override
  Future<void> write({required String key, required String? value}) async {
    if (value == null) {
      _store.remove(key);
    } else {
      _store[key] = value;
    }
  }

  @override
  Future<String?> read({required String key}) async => _store[key];
}

void main() {
  test('saveAccessToken then readAccessToken returns the same value', () async {
    final storage = TokenStorage(FakeSecureStore());

    await storage.saveAccessToken('abc.def.ghi');
    final result = await storage.readAccessToken();

    expect(result, 'abc.def.ghi');
  });

  test('clear removes the stored token', () async {
    final storage = TokenStorage(FakeSecureStore());
    await storage.saveAccessToken('abc.def.ghi');

    await storage.clear();
    final result = await storage.readAccessToken();

    expect(result, isNull);
  });
}
