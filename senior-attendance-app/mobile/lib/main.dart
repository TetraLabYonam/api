import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'design_system/atm_colors.dart';
import 'features/auth/auth_provider.dart';
import 'features/auth/login_screen.dart';
import 'features/checkin/checkin_screen.dart';
import 'features/consent/consent_screen.dart';

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
    );
  }
}

/// 앱 시작 시 저장된 accessToken과 회원 정보(위치정보 동의 여부)를 조회해
/// 로그인 화면 / 위치정보 동의 화면 / 체크인 화면 중 하나로 분기한다.
class AuthGate extends ConsumerWidget {
  const AuthGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isLoggedIn = ref.watch(isLoggedInProvider);
    return isLoggedIn.when(
      data: (loggedIn) {
        if (!loggedIn) return const LoginScreen();
        final me = ref.watch(meProvider);
        return me.when(
          data: (info) => info.locationConsentAgreed ? const CheckinScreen() : const ConsentScreen(),
          loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
          error: (_, _) => const LoginScreen(),
        );
      },
      loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (_, _) => const LoginScreen(),
    );
  }
}
