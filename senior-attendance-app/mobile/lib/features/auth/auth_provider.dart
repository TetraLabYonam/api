import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api_client.dart';
import '../../core/token_storage.dart';
import 'auth_repository.dart';

final apiClientProvider = Provider<ApiClient>((ref) {
  return ApiClient(baseUrl: 'http://10.0.2.2:8080');
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final apiClient = ref.watch(apiClientProvider);
  return AuthRepository(dio: apiClient.dio, tokenStorage: apiClient.tokenStorage);
});

final isLoggedInProvider = FutureProvider<bool>((ref) async {
  final token = await TokenStorage().readAccessToken();
  return token != null;
});

final meProvider = FutureProvider<({bool locationConsentAgreed, int? assignedPlaceId})>((ref) {
  return ref.watch(authRepositoryProvider).me();
});
