import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract class SecureStore {
  Future<void> write({required String key, required String? value});
  Future<String?> read({required String key});
}

class FlutterSecureStore implements SecureStore {
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  @override
  Future<void> write({required String key, required String? value}) =>
      _storage.write(key: key, value: value);

  @override
  Future<String?> read({required String key}) => _storage.read(key: key);
}

class TokenStorage {
  static const _accessTokenKey = 'access_token';

  final SecureStore _store;

  TokenStorage([SecureStore? store]) : _store = store ?? FlutterSecureStore();

  Future<void> saveAccessToken(String token) =>
      _store.write(key: _accessTokenKey, value: token);

  Future<String?> readAccessToken() => _store.read(key: _accessTokenKey);

  Future<void> clear() => _store.write(key: _accessTokenKey, value: null);
}
