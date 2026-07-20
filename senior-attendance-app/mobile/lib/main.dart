import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'design_system/atm_colors.dart';
import 'features/auth/auth_provider.dart';
import 'features/auth/phone_login_screen.dart';
import 'features/unit_selection/unit_selection_screen.dart';

void main() {
  runApp(const ProviderScope(child: MyApp()));
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '시니어 일자리 근태',
      theme: ThemeData(
        scaffoldBackgroundColor: AtmColors.background,
        colorScheme: ColorScheme.fromSeed(seedColor: AtmColors.primary),
        appBarTheme: const AppBarTheme(
          backgroundColor: AtmColors.background,
          foregroundColor: AtmColors.primary,
          elevation: 0,
          centerTitle: false,
          titleTextStyle: TextStyle(color: AtmColors.primary, fontSize: 20, fontWeight: FontWeight.w800),
        ),
      ),
      home: const AuthGate(),
      routes: {
        '/unit-selection': (context) => const UnitSelectionScreen(),
      },
    );
  }
}

/// 앱 시작 시 저장된 accessToken 유무로 로그인 화면과 사업단 선택 화면 중 하나로 분기한다.
class AuthGate extends ConsumerWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isLoggedIn = ref.watch(isLoggedInProvider);
    return isLoggedIn.when(
      data: (loggedIn) => loggedIn ? const UnitSelectionScreen() : const PhoneLoginScreen(),
      loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (_, _) => const PhoneLoginScreen(),
    );
  }
}
