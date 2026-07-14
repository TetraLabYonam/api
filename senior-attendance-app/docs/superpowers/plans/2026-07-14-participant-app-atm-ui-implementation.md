# 참여자용 모바일 ATM 스타일 UI 구현 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 참여자용 Flutter 6개 화면(phone_login, otp_verify, unit_selection, job_search, consent, checkin)을 공용 ATM 스타일 위젯으로 리팩토링하고, 체크인 화면이 실제 "오늘 일정"을 조회하도록 백엔드 API를 추가한다.

**Architecture:** `mobile/lib/design_system/`에 상태 없는 순수 표현 위젯 5종(색상, 버튼 2종, 하단바, 목록 항목, 키패드)을 먼저 만들고, 6개 화면이 이를 조합해서 쓰도록 리팩토링한다. 백엔드는 기존 `AttendService`/`AttendController`에 `GET /api/v1/attend/today` 엔드포인트를 추가해, 체크인 화면의 하드코딩된 `scheduleId: 1`을 실제 조회 결과로 대체한다.

**Tech Stack:** Flutter/Dart(flutter_riverpod, dio), Spring Boot 3.5.6/Java 17(JPA, JUnit5, Mockito, TestRestTemplate)

## Global Constraints

- 색상: Primary(진행) `#2E9E4F`, Secondary(취소/이전/아니오) `#F5821F`, 배경 `#F7F7F7` — `docs/superpowers/specs/2026-07-14-atm-style-app-redesign-design.md` 기준
- 질문 텍스트는 항상 검정(`Colors.black`), 굵게
- 버튼/목록 항목 모서리는 각짐(둥근 모서리 없음), 최소 높이 64~72
- 신규 도메인/테이블 변경 금지 (기존 `Attend`/`Schedule`/`Member`/`Place` 그대로 사용)
- 신규 라우트 추가 금지 — 기존 `Navigator.push`/`pushNamedAndRemoveUntil('/unit-selection', ...)` 구조 유지
- 기존 에러 메시지 문자열은 그대로 재사용 (표시 위치/스타일만 변경)
- 프론트 테스트는 `mobile/test/support/fake_api_client.dart`의 `fakeApiClient`/`jsonResponse` 패턴을 그대로 사용
- 백엔드 통합 테스트는 `obtainMemberAccessToken(phoneNumber)` 패턴(OTP 요청→SMS 캡처→검증)을 그대로 재사용

---

## File Structure

**프론트엔드 신규 생성**
- `mobile/lib/design_system/atm_colors.dart`
- `mobile/lib/design_system/atm_primary_button.dart`
- `mobile/lib/design_system/atm_secondary_button.dart`
- `mobile/lib/design_system/atm_bottom_action_bar.dart`
- `mobile/lib/design_system/atm_option_list_item.dart`
- `mobile/lib/design_system/atm_numeric_keypad.dart`
- `mobile/test/design_system/atm_primary_button_test.dart`
- `mobile/test/design_system/atm_secondary_button_test.dart`
- `mobile/test/design_system/atm_bottom_action_bar_test.dart`
- `mobile/test/design_system/atm_option_list_item_test.dart`
- `mobile/test/design_system/atm_numeric_keypad_test.dart`

**프론트엔드 수정**
- `mobile/lib/features/auth/phone_login_screen.dart`
- `mobile/lib/features/auth/otp_verify_screen.dart`
- `mobile/lib/features/unit_selection/unit_selection_screen.dart`
- `mobile/lib/features/job_search/job_search_screen.dart`
- `mobile/lib/features/consent/consent_screen.dart`
- `mobile/lib/features/checkin/checkin_repository.dart`
- `mobile/lib/features/checkin/checkin_screen.dart`
- `mobile/test/features/auth/phone_login_screen_test.dart`
- `mobile/test/features/auth/otp_verify_screen_test.dart`
- `mobile/test/features/unit_selection/unit_selection_screen_test.dart`
- `mobile/test/features/job_search/job_search_screen_test.dart`
- `mobile/test/features/consent/consent_screen_test.dart`
- `mobile/test/features/checkin/checkin_repository_test.dart`
- `mobile/test/features/checkin/checkin_screen_test.dart`

**백엔드 신규 생성**
- `backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java`
- `backend/src/main/java/com/example/attempt/dto/attend/AttendTodayResponse.java`

**백엔드 수정**
- `backend/src/main/java/com/example/attempt/service/AttendService.java`
- `backend/src/main/java/com/example/attempt/controller/AttendController.java`
- `backend/src/test/java/com/example/attempt/service/AttendServiceTest.java`
- `backend/src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java`

---

## Task 1: AtmColors + AtmPrimaryButton

**Files:**
- Create: `mobile/lib/design_system/atm_colors.dart`
- Create: `mobile/lib/design_system/atm_primary_button.dart`
- Test: `mobile/test/design_system/atm_primary_button_test.dart`

**Interfaces:**
- Produces: `AtmColors.primary`, `AtmColors.secondary`, `AtmColors.background` (`Color` 상수) / `AtmPrimaryButton({required String label, required VoidCallback? onPressed})` 위젯

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/design_system/atm_primary_button_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_colors.dart';
import 'package:senior_job_attendance/design_system/atm_primary_button.dart';

void main() {
  testWidgets('라벨을 표시하고 탭하면 onPressed가 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmPrimaryButton(label: '확인', onPressed: () => tapped = true)),
    ));

    expect(find.text('확인'), findsOneWidget);
    await tester.tap(find.byType(AtmPrimaryButton));
    expect(tapped, isTrue);
  });

  testWidgets('onPressed가 null이면 비활성화된다', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: AtmPrimaryButton(label: '확인', onPressed: null)),
    ));

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.onPressed, isNull);
  });

  testWidgets('그린 배경으로 렌더링된다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmPrimaryButton(label: '확인', onPressed: () {})),
    ));

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.style?.backgroundColor?.resolve({}), AtmColors.primary);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/design_system/atm_primary_button_test.dart`
Expected: FAIL — `Target of URI doesn't exist: 'package:senior_job_attendance/design_system/atm_colors.dart'`

- [ ] **Step 3: Write minimal implementation**

```dart
// mobile/lib/design_system/atm_colors.dart
import 'package:flutter/material.dart';

class AtmColors {
  static const primary = Color(0xFF2E9E4F);
  static const secondary = Color(0xFFF5821F);
  static const background = Color(0xFFF7F7F7);
}
```

```dart
// mobile/lib/design_system/atm_primary_button.dart
import 'package:flutter/material.dart';
import 'atm_colors.dart';

class AtmPrimaryButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;

  const AtmPrimaryButton({super.key, required this.label, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: AtmColors.primary,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(72),
          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(2))),
        ),
        child: Text(label, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/design_system/atm_primary_button_test.dart`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/design_system/atm_colors.dart mobile/lib/design_system/atm_primary_button.dart mobile/test/design_system/atm_primary_button_test.dart
git commit -m "feat(design-system): add AtmColors and AtmPrimaryButton"
```

---

## Task 2: AtmSecondaryButton

**Files:**
- Create: `mobile/lib/design_system/atm_secondary_button.dart`
- Test: `mobile/test/design_system/atm_secondary_button_test.dart`

**Interfaces:**
- Consumes: `AtmColors.secondary` (Task 1)
- Produces: `AtmSecondaryButton({required String label, required VoidCallback? onPressed})` 위젯

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/design_system/atm_secondary_button_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_colors.dart';
import 'package:senior_job_attendance/design_system/atm_secondary_button.dart';

void main() {
  testWidgets('라벨을 표시하고 탭하면 onPressed가 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmSecondaryButton(label: '취소', onPressed: () => tapped = true)),
    ));

    expect(find.text('취소'), findsOneWidget);
    await tester.tap(find.byType(AtmSecondaryButton));
    expect(tapped, isTrue);
  });

  testWidgets('오렌지 배경으로 렌더링된다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(body: AtmSecondaryButton(label: '취소', onPressed: () {})),
    ));

    final button = tester.widget<ElevatedButton>(find.byType(ElevatedButton));
    expect(button.style?.backgroundColor?.resolve({}), AtmColors.secondary);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/design_system/atm_secondary_button_test.dart`
Expected: FAIL — `Target of URI doesn't exist: 'package:senior_job_attendance/design_system/atm_secondary_button.dart'`

- [ ] **Step 3: Write minimal implementation**

```dart
// mobile/lib/design_system/atm_secondary_button.dart
import 'package:flutter/material.dart';
import 'atm_colors.dart';

class AtmSecondaryButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;

  const AtmSecondaryButton({super.key, required this.label, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: AtmColors.secondary,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(72),
          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(2))),
        ),
        child: Text(label, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/design_system/atm_secondary_button_test.dart`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/design_system/atm_secondary_button.dart mobile/test/design_system/atm_secondary_button_test.dart
git commit -m "feat(design-system): add AtmSecondaryButton"
```

---

## Task 3: AtmBottomActionBar

**Files:**
- Create: `mobile/lib/design_system/atm_bottom_action_bar.dart`
- Test: `mobile/test/design_system/atm_bottom_action_bar_test.dart`

**Interfaces:**
- Consumes: `AtmColors.primary`, `AtmColors.secondary` (Task 1)
- Produces: `AtmBottomActionBar.single({required String label, required VoidCallback? onPressed})`, `AtmBottomActionBar.confirm({required VoidCallback? onYes, required VoidCallback? onNo})` — 각각 취소/이전 단일 바, 네/아니오 분할 바를 렌더링. 텍스트는 각각 `label`, 고정 문자열 `'네'`/`'아니오'`.

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/design_system/atm_bottom_action_bar_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_bottom_action_bar.dart';

void main() {
  testWidgets('single 변형은 라벨 하나만 보여주고 탭하면 콜백이 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        bottomNavigationBar: AtmBottomActionBar.single(label: '이전', onPressed: () => tapped = true),
      ),
    ));

    expect(find.text('이전'), findsOneWidget);
    expect(find.text('네'), findsNothing);
    await tester.tap(find.text('이전'));
    expect(tapped, isTrue);
  });

  testWidgets('confirm 변형은 네/아니오 버튼을 보여주고 각각 콜백을 호출한다', (tester) async {
    bool yesTapped = false;
    bool noTapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        bottomNavigationBar: AtmBottomActionBar.confirm(
          onYes: () => yesTapped = true,
          onNo: () => noTapped = true,
        ),
      ),
    ));

    await tester.tap(find.text('네'));
    expect(yesTapped, isTrue);

    await tester.tap(find.text('아니오'));
    expect(noTapped, isTrue);
  });

  testWidgets('confirm 변형에서 콜백이 null이면 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(
        bottomNavigationBar: AtmBottomActionBar.confirm(onYes: null, onNo: null),
      ),
    ));

    final buttons = tester.widgetList<ElevatedButton>(find.byType(ElevatedButton));
    expect(buttons.every((b) => b.onPressed == null), isTrue);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/design_system/atm_bottom_action_bar_test.dart`
Expected: FAIL — `Target of URI doesn't exist: 'package:senior_job_attendance/design_system/atm_bottom_action_bar.dart'`

- [ ] **Step 3: Write minimal implementation**

```dart
// mobile/lib/design_system/atm_bottom_action_bar.dart
import 'package:flutter/material.dart';
import 'atm_colors.dart';

class AtmBottomActionBar extends StatelessWidget {
  final String? singleLabel;
  final VoidCallback? onSingleTap;
  final VoidCallback? onYesTap;
  final VoidCallback? onNoTap;
  final bool _isConfirm;

  const AtmBottomActionBar.single({super.key, required String label, required VoidCallback? onPressed})
      : singleLabel = label,
        onSingleTap = onPressed,
        onYesTap = null,
        onNoTap = null,
        _isConfirm = false;

  const AtmBottomActionBar.confirm({super.key, required VoidCallback? onYes, required VoidCallback? onNo})
      : singleLabel = null,
        onSingleTap = null,
        onYesTap = onYes,
        onNoTap = onNo,
        _isConfirm = true;

  ButtonStyle _style(Color background) {
    return ElevatedButton.styleFrom(
      backgroundColor: background,
      foregroundColor: Colors.white,
      minimumSize: const Size.fromHeight(64),
      shape: const RoundedRectangleBorder(),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!_isConfirm) {
      return SizedBox(
        width: double.infinity,
        child: ElevatedButton(
          onPressed: onSingleTap,
          style: _style(AtmColors.secondary),
          child: Text(singleLabel!, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        ),
      );
    }
    return Row(
      children: [
        Expanded(
          child: ElevatedButton(
            onPressed: onYesTap,
            style: _style(AtmColors.primary),
            child: const Text('네', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
        ),
        Expanded(
          child: ElevatedButton(
            onPressed: onNoTap,
            style: _style(AtmColors.secondary),
            child: const Text('아니오', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
        ),
      ],
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/design_system/atm_bottom_action_bar_test.dart`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/design_system/atm_bottom_action_bar.dart mobile/test/design_system/atm_bottom_action_bar_test.dart
git commit -m "feat(design-system): add AtmBottomActionBar with single and confirm variants"
```

---

## Task 4: AtmOptionListItem

**Files:**
- Create: `mobile/lib/design_system/atm_option_list_item.dart`
- Test: `mobile/test/design_system/atm_option_list_item_test.dart`

**Interfaces:**
- Consumes: `AtmColors.primary` (Task 1)
- Produces: `AtmOptionListItem({required String title, String? subtitle, VoidCallback? onTap})` 위젯

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/design_system/atm_option_list_item_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_option_list_item.dart';

void main() {
  testWidgets('제목과 부제목을 보여주고 탭하면 onTap이 호출된다', (tester) async {
    bool tapped = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmOptionListItem(title: '사업단 A', subtitle: '설명', onTap: () => tapped = true),
      ),
    ));

    expect(find.text('사업단 A'), findsOneWidget);
    expect(find.text('설명'), findsOneWidget);
    await tester.tap(find.text('사업단 A'));
    expect(tapped, isTrue);
  });

  testWidgets('부제목이 없으면 표시하지 않는다', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: AtmOptionListItem(title: '사업단 A')),
    ));

    expect(find.text('사업단 A'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/design_system/atm_option_list_item_test.dart`
Expected: FAIL — `Target of URI doesn't exist: 'package:senior_job_attendance/design_system/atm_option_list_item.dart'`

- [ ] **Step 3: Write minimal implementation**

```dart
// mobile/lib/design_system/atm_option_list_item.dart
import 'package:flutter/material.dart';
import 'atm_colors.dart';

class AtmOptionListItem extends StatelessWidget {
  final String title;
  final String? subtitle;
  final VoidCallback? onTap;

  const AtmOptionListItem({super.key, required this.title, this.subtitle, this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        constraints: const BoxConstraints(minHeight: 72),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        color: AtmColors.primary,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(title, style: const TextStyle(color: Colors.white, fontSize: 19, fontWeight: FontWeight.bold)),
            if (subtitle != null) ...[
              const SizedBox(height: 2),
              Text(subtitle!, style: const TextStyle(color: Colors.white70, fontSize: 14)),
            ],
          ],
        ),
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/design_system/atm_option_list_item_test.dart`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/design_system/atm_option_list_item.dart mobile/test/design_system/atm_option_list_item_test.dart
git commit -m "feat(design-system): add AtmOptionListItem"
```

---

## Task 5: AtmNumericKeypad

**Files:**
- Create: `mobile/lib/design_system/atm_numeric_keypad.dart`
- Test: `mobile/test/design_system/atm_numeric_keypad_test.dart`

**Interfaces:**
- Consumes: `AtmColors.primary`, `AtmColors.secondary` (Task 1)
- Produces: `AtmNumericKeypad({required ValueChanged<String> onDigit, required VoidCallback onBackspace, required VoidCallback? onConfirm, String confirmLabel = '확인'})` — `onConfirm`이 `null`이면 확인 키가 비활성화된다.

- [ ] **Step 1: Write the failing test**

```dart
// mobile/test/design_system/atm_numeric_keypad_test.dart
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/design_system/atm_numeric_keypad.dart';

void main() {
  testWidgets('숫자를 탭하면 onDigit이 해당 숫자로 호출된다', (tester) async {
    String? tapped;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(onDigit: (d) => tapped = d, onBackspace: () {}, onConfirm: () {}),
      ),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '5'));
    expect(tapped, '5');
  });

  testWidgets('지우기를 탭하면 onBackspace가 호출된다', (tester) async {
    bool backspaced = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(onDigit: (_) {}, onBackspace: () => backspaced = true, onConfirm: () {}),
      ),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '지우기'));
    expect(backspaced, isTrue);
  });

  testWidgets('confirmLabel을 탭하면 onConfirm이 호출된다', (tester) async {
    bool confirmed = false;
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(
          onDigit: (_) {},
          onBackspace: () {},
          onConfirm: () => confirmed = true,
          confirmLabel: '인증받기',
        ),
      ),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '인증받기'));
    expect(confirmed, isTrue);
  });

  testWidgets('onConfirm이 null이면 확인 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AtmNumericKeypad(onDigit: (_) {}, onBackspace: () {}, onConfirm: null),
      ),
    ));

    final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '확인'));
    expect(button.onPressed, isNull);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/design_system/atm_numeric_keypad_test.dart`
Expected: FAIL — `Target of URI doesn't exist: 'package:senior_job_attendance/design_system/atm_numeric_keypad.dart'`

- [ ] **Step 3: Write minimal implementation**

```dart
// mobile/lib/design_system/atm_numeric_keypad.dart
import 'package:flutter/material.dart';
import 'atm_colors.dart';

class AtmNumericKeypad extends StatelessWidget {
  final ValueChanged<String> onDigit;
  final VoidCallback onBackspace;
  final VoidCallback? onConfirm;
  final String confirmLabel;

  const AtmNumericKeypad({
    super.key,
    required this.onDigit,
    required this.onBackspace,
    required this.onConfirm,
    this.confirmLabel = '확인',
  });

  Widget _key(String label, {VoidCallback? onTap, Color? background, Color? foreground}) {
    return ElevatedButton(
      onPressed: onTap,
      style: ElevatedButton.styleFrom(
        backgroundColor: background,
        foregroundColor: foreground ?? Colors.black87,
        minimumSize: const Size.fromHeight(56),
        shape: const RoundedRectangleBorder(),
      ),
      child: Text(label, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return GridView.count(
      crossAxisCount: 3,
      shrinkWrap: true,
      mainAxisSpacing: 6,
      crossAxisSpacing: 6,
      physics: const NeverScrollableScrollPhysics(),
      children: [
        for (final d in ['1', '2', '3', '4', '5', '6', '7', '8', '9']) _key(d, onTap: () => onDigit(d)),
        _key('지우기', onTap: onBackspace, background: AtmColors.secondary, foreground: Colors.white),
        _key('0', onTap: () => onDigit('0')),
        _key(confirmLabel, onTap: onConfirm, background: AtmColors.primary, foreground: Colors.white),
      ],
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/design_system/atm_numeric_keypad_test.dart`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/design_system/atm_numeric_keypad.dart mobile/test/design_system/atm_numeric_keypad_test.dart
git commit -m "feat(design-system): add AtmNumericKeypad"
```

---

## Task 6: 백엔드 — AttendTodayResponse DTO + AttendService.findTodayAttend()

**Files:**
- Create: `backend/src/main/java/com/example/attempt/dto/attend/AttendTodayResponse.java`
- Modify: `backend/src/main/java/com/example/attempt/service/AttendService.java`
- Test: `backend/src/test/java/com/example/attempt/service/AttendServiceTest.java`

**Interfaces:**
- Consumes: `AttendRepository.findByMemberIdAndDateRange(Long, LocalDate, LocalDate)` (기존), `Attend.getSchedule()`/`Schedule.getPlace()` (기존)
- Produces: `AttendTodayResponse.none()`, `AttendTodayResponse.of(Attend)`; `AttendService.findTodayAttend(Long memberId)` → `Optional<Attend>`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/example/attempt/service/AttendServiceTest.java`의 기존 `import` 블록에 `java.time.LocalDate`, `java.util.List`를 추가하고(이미 `java.util.Optional`은 있음), `static org.mockito.ArgumentMatchers.eq`를 추가한 뒤 파일 끝(마지막 `}` 앞)에 아래 두 테스트를 추가한다.

```java
    @Test
    void findTodayAttend_withScheduleToday_returnsIt() {
        Schedule schedule = scheduleStartingAt(LocalTime.now(), placeAt(35.30, 129.00));
        Attend attend = scheduledAttend(schedule);
        when(attendRepository.findByMemberIdAndDateRange(eq(100L), any(), any()))
                .thenReturn(List.of(attend));

        Optional<Attend> result = service.findTodayAttend(100L);

        assertTrue(result.isPresent());
        assertEquals(attend, result.get());
    }

    @Test
    void findTodayAttend_noScheduleToday_returnsEmpty() {
        when(attendRepository.findByMemberIdAndDateRange(eq(100L), any(), any()))
                .thenReturn(List.of());

        Optional<Attend> result = service.findTodayAttend(100L);

        assertTrue(result.isEmpty());
    }
```

전체 import 블록은 다음과 같아야 한다(기존 줄 순서 유지, 추가분만 반영):

```java
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.dto.attend.AttendCheckInRequest;
import com.example.attempt.dto.attend.AttendCheckInResponse;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.AttendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AttendServiceTest"`
Expected: FAIL — `cannot find symbol: method findTodayAttend(Long)`

- [ ] **Step 3: Write minimal implementation**

```java
// backend/src/main/java/com/example/attempt/dto/attend/AttendTodayResponse.java
package com.example.attempt.dto.attend;

import com.example.attempt.domain.Attend;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

/**
 * 로그인한 회원의 "오늘 일정" 조회 응답 DTO.
 * 오늘 배정된 Attend가 없는 것은 정상 상태이므로 404가 아니라 hasSchedule=false로 표현한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendTodayResponse {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private boolean hasSchedule;
    private Long scheduleId;
    private String placeName;
    private String startTime;
    private String endTime;

    public static AttendTodayResponse none() {
        return AttendTodayResponse.builder().hasSchedule(false).build();
    }

    public static AttendTodayResponse of(Attend attend) {
        var schedule = attend.getSchedule();
        return AttendTodayResponse.builder()
                .hasSchedule(true)
                .scheduleId(schedule.getId())
                .placeName(schedule.getPlace().getName())
                .startTime(schedule.getStartTime() != null ? schedule.getStartTime().format(TIME_FORMAT) : null)
                .endTime(schedule.getEndTime() != null ? schedule.getEndTime().format(TIME_FORMAT) : null)
                .build();
    }
}
```

`AttendService`에 아래 메서드를 추가하고(클래스 내 아무 위치나, `checkIn` 메서드 뒤 권장), 파일 상단 import에 `java.time.LocalDate`와 `java.util.List`, `java.util.Optional`을 추가한다.

```java
    /**
     * 로그인한 회원의 오늘 Attend를 조회한다. 하루 여러 건이면 첫 건만 사용한다(통상 1건).
     */
    public Optional<Attend> findTodayAttend(Long memberId) {
        LocalDate today = LocalDate.now();
        List<Attend> attends = attendRepository.findByMemberIdAndDateRange(memberId, today, today);
        return attends.stream().findFirst();
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.service.AttendServiceTest"`
Expected: PASS (9 tests: 기존 7 + 신규 2)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/dto/attend/AttendTodayResponse.java backend/src/main/java/com/example/attempt/service/AttendService.java backend/src/test/java/com/example/attempt/service/AttendServiceTest.java
git commit -m "feat(attend): add AttendTodayResponse and AttendService.findTodayAttend"
```

---

## Task 7: 백엔드 — GET /api/v1/attend/today 엔드포인트

**Files:**
- Create: `backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java`
- Modify: `backend/src/main/java/com/example/attempt/controller/AttendController.java`
- Test: `backend/src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `AttendService.findTodayAttend(Long)` (Task 6), `AttendTodayResponse.of/none` (Task 6), `CurrentMemberService.getCurrentMember()` (기존)
- Produces: `GET /api/v1/attend/today` → 200 `{"hasSchedule": bool, "scheduleId"?: number, "placeName"?: string, "startTime"?: "HH:mm", "endTime"?: "HH:mm"}`

- [ ] **Step 1: Write the failing test**

`AttendControllerIntegrationTest.java` 상단 import에 아래를 추가한다.

```java
import com.example.attempt.domain.Attend;
import com.example.attempt.domain.AttendStatus;
import com.example.attempt.domain.Member;
import com.example.attempt.domain.Place;
import com.example.attempt.domain.Schedule;
import com.example.attempt.repository.AttendRepository;
import com.example.attempt.repository.MemberRepository;
import com.example.attempt.repository.PlaceRepository;
import com.example.attempt.repository.ScheduleRepository;

import java.time.LocalDate;
import java.time.LocalTime;
```

클래스 필드에 아래 4개 `@Autowired`를 추가하고, 기존 `checkIn_withAuthButWithoutConsent_returns409()` 테스트 뒤(파일 끝 `}` 앞)에 `authHeaders` 헬퍼와 신규 테스트 3개를 추가한다.

```java
    @Autowired
    AttendRepository attendRepository;

    @Autowired
    ScheduleRepository scheduleRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    private HttpHeaders authHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    @Test
    void today_withoutAuth_returns401() {
        ResponseEntity<Object> resp = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/attend/today", Object.class);

        assertEquals(401, resp.getStatusCodeValue());
    }

    @Test
    void today_withoutScheduleToday_returnsHasScheduleFalse() {
        String accessToken = obtainMemberAccessToken("01066667777");

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/attend/today",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(false, resp.getBody().get("hasSchedule"));
    }

    @Test
    void today_withScheduledAttendToday_returnsScheduleInfo() {
        String phoneNumber = "01055556666";
        String accessToken = obtainMemberAccessToken(phoneNumber);
        Member member = memberRepository.findByPhoneNumber(phoneNumber).orElseThrow();

        Place place = placeRepository.save(new Place("중앙공원", "주소", 35.3, 129.0));
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .title("오전 근무")
                .scheduleDate(LocalDate.now())
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(13, 0))
                .place(place)
                .build());
        attendRepository.save(Attend.builder()
                .member(member)
                .schedule(schedule)
                .status(AttendStatus.SCHEDULED)
                .build());

        ResponseEntity<Map> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/attend/today",
                HttpMethod.GET, new HttpEntity<>(authHeaders(accessToken)), Map.class);

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals(true, resp.getBody().get("hasSchedule"));
        assertEquals("중앙공원", resp.getBody().get("placeName"));
        assertEquals("09:00", resp.getBody().get("startTime"));
        assertEquals("13:00", resp.getBody().get("endTime"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.AttendControllerIntegrationTest"`
Expected: FAIL — `package com.example.attempt.repository does not contain ScheduleRepository` (컴파일 실패)

- [ ] **Step 3: Write minimal implementation**

```java
// backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java
package com.example.attempt.repository;

import com.example.attempt.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
}
```

`AttendController`에 아래 엔드포인트를 추가하고, 상단 import에 `com.example.attempt.dto.attend.AttendTodayResponse`를 추가한다.

```java
    @GetMapping("/today")
    public AttendTodayResponse today() {
        Member member = currentMemberService.getCurrentMember();
        return attendService.findTodayAttend(member.getId())
                .map(AttendTodayResponse::of)
                .orElseGet(AttendTodayResponse::none);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.example.attempt.controller.AttendControllerIntegrationTest"`
Expected: PASS (5 tests: 기존 2 + 신규 3)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/example/attempt/repository/ScheduleRepository.java backend/src/main/java/com/example/attempt/controller/AttendController.java backend/src/test/java/com/example/attempt/controller/AttendControllerIntegrationTest.java
git commit -m "feat(attend): add GET /api/v1/attend/today endpoint"
```

---

## Task 8: phone_login_screen 리팩토링

**Files:**
- Modify: `mobile/lib/features/auth/phone_login_screen.dart`
- Modify: `mobile/test/features/auth/phone_login_screen_test.dart`

**Interfaces:**
- Consumes: `AtmNumericKeypad` (Task 5), `AtmBottomActionBar.single` (Task 3), `AuthRepository.requestOtp(String)` (기존, 변경 없음)

- [ ] **Step 1: Write the failing test**

`phone_login_screen_test.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/phone_login_screen.dart';

import '../../support/fake_api_client.dart';

Future<void> _enterDigits(WidgetTester tester, String digits) async {
  for (final digit in digits.split('')) {
    await tester.tap(find.widgetWithText(ElevatedButton, digit));
    await tester.pump();
  }
}

void main() {
  testWidgets('전화번호 입력 후 인증받기를 누르면 OTP 요청 후 인증번호 입력 화면으로 이동한다', (tester) async {
    bool otpRequested = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/member-auth/otp/request') {
            otpRequested = true;
            return jsonResponse('{}');
          }
          throw StateError('예상치 못한 요청: ${options.path}');
        })),
      ],
      child: const MaterialApp(home: PhoneLoginScreen()),
    ));

    await _enterDigits(tester, '01012345678');
    await tester.tap(find.widgetWithText(ElevatedButton, '인증받기'));
    await tester.pumpAndSettle();

    expect(otpRequested, isTrue);
    expect(find.text('인증번호 입력'), findsOneWidget);
  });

  testWidgets('전화번호를 입력하지 않으면 인증받기 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: MaterialApp(home: PhoneLoginScreen())));

    final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '인증받기'));
    expect(button.onPressed, isNull);
  });

  testWidgets('요청 처리 중에는 확인 버튼이 비활성화되고 전송 중으로 표시된다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          await Future<void>.delayed(const Duration(milliseconds: 100));
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: PhoneLoginScreen()),
    ));

    await _enterDigits(tester, '01012345678');
    await tester.tap(find.widgetWithText(ElevatedButton, '인증받기'));
    await tester.pump();

    expect(find.text('전송 중...'), findsOneWidget);
    final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '전송 중...'));
    expect(button.onPressed, isNull);

    await tester.pumpAndSettle();
  });

  testWidgets('취소를 누르면 입력한 전화번호가 지워진다', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: MaterialApp(home: PhoneLoginScreen())));

    await _enterDigits(tester, '010');
    expect(find.text('010'), findsOneWidget);

    await tester.tap(find.widgetWithText(ElevatedButton, '취소'));
    await tester.pump();

    expect(find.text('010'), findsNothing);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/auth/phone_login_screen_test.dart`
Expected: FAIL — `find.widgetWithText(ElevatedButton, '1')` 매칭 위젯 없음 (기존 화면에 키패드가 없음)

- [ ] **Step 3: Write minimal implementation**

`phone_login_screen.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_numeric_keypad.dart';
import 'auth_provider.dart';
import 'otp_verify_screen.dart';

class PhoneLoginScreen extends ConsumerStatefulWidget {
  const PhoneLoginScreen({super.key});

  @override
  ConsumerState<PhoneLoginScreen> createState() => _PhoneLoginScreenState();
}

class _PhoneLoginScreenState extends ConsumerState<PhoneLoginScreen> {
  String _phoneNumber = '';
  bool _sending = false;

  void _onDigit(String digit) {
    if (_phoneNumber.length >= 11) return;
    setState(() => _phoneNumber += digit);
  }

  void _onBackspace() {
    if (_phoneNumber.isEmpty) return;
    setState(() => _phoneNumber = _phoneNumber.substring(0, _phoneNumber.length - 1));
  }

  Future<void> _sendOtp() async {
    setState(() => _sending = true);
    try {
      await ref.read(authRepositoryProvider).requestOtp(_phoneNumber);
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(
        builder: (_) => OtpVerifyScreen(phoneNumber: _phoneNumber),
      ));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('로그인')),
      body: Column(
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 24, 20, 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('전화번호를\n입력해주세요',
                  style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: Colors.black)),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(border: Border.all(color: const Color(0xFF2E9E4F), width: 2)),
              child: Text(_phoneNumber, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: AtmNumericKeypad(
              onDigit: _onDigit,
              onBackspace: _onBackspace,
              onConfirm: (_sending || _phoneNumber.isEmpty) ? null : _sendOtp,
              confirmLabel: _sending ? '전송 중...' : '인증받기',
            ),
          ),
          const Spacer(),
          AtmBottomActionBar.single(label: '취소', onPressed: () => setState(() => _phoneNumber = '')),
        ],
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/auth/phone_login_screen_test.dart`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/auth/phone_login_screen.dart mobile/test/features/auth/phone_login_screen_test.dart
git commit -m "refactor(phone-login): use AtmNumericKeypad and AtmBottomActionBar"
```

---

## Task 9: otp_verify_screen 리팩토링

**Files:**
- Modify: `mobile/lib/features/auth/otp_verify_screen.dart`
- Modify: `mobile/test/features/auth/otp_verify_screen_test.dart`

**Interfaces:**
- Consumes: `AtmNumericKeypad` (Task 5), `AtmBottomActionBar.single` (Task 3), `AuthRepository.verifyOtp(String, String)` (기존, 변경 없음)

- [ ] **Step 1: Write the failing test**

`otp_verify_screen_test.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/auth/otp_verify_screen.dart';
import 'package:senior_job_attendance/features/unit_selection/unit_selection_screen.dart';

import '../../support/fake_api_client.dart';

Future<void> _enterDigits(WidgetTester tester, String digits) async {
  for (final digit in digits.split('')) {
    await tester.tap(find.widgetWithText(ElevatedButton, digit));
    await tester.pump();
  }
}

void main() {
  Widget appWithRoutes(Widget home) {
    return MaterialApp(
      home: home,
      routes: {
        '/unit-selection': (context) => const UnitSelectionScreen(),
      },
    );
  }

  testWidgets('6자리를 모두 입력하면 확인 버튼이 활성화되고, 정확하면 사업단 유형 선택 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/member-auth/otp/verify') {
            return jsonResponse('{"accessToken":"fake-token","memberId":1}');
          }
          throw StateError('예상치 못한 요청: ${options.path}');
        })),
      ],
      child: appWithRoutes(const OtpVerifyScreen(phoneNumber: '01012345678')),
    ));

    await _enterDigits(tester, '123456');
    await tester.tap(find.widgetWithText(ElevatedButton, '확인'));
    await tester.pumpAndSettle();

    expect(find.text('사업단 유형 선택'), findsOneWidget);
  });

  testWidgets('6자리를 채우기 전에는 확인 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(appWithRoutes(const OtpVerifyScreen(phoneNumber: '01012345678')));

    await _enterDigits(tester, '123');

    final button = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '확인'));
    expect(button.onPressed, isNull);
  });

  testWidgets('틀린 인증번호를 입력하면 에러 메시지를 보여주고 화면을 유지한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"error":"인증번호가 올바르지 않습니다."}', statusCode: 401);
        })),
      ],
      child: appWithRoutes(const OtpVerifyScreen(phoneNumber: '01012345678')),
    ));

    await _enterDigits(tester, '000000');
    await tester.tap(find.widgetWithText(ElevatedButton, '확인'));
    await tester.pumpAndSettle();

    expect(find.text('인증번호가 올바르지 않습니다.'), findsOneWidget);
    expect(find.text('인증번호 입력'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/auth/otp_verify_screen_test.dart`
Expected: FAIL — `find.widgetWithText(ElevatedButton, '1')` 매칭 위젯 없음

- [ ] **Step 3: Write minimal implementation**

`otp_verify_screen.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_numeric_keypad.dart';
import 'auth_provider.dart';

class OtpVerifyScreen extends ConsumerStatefulWidget {
  final String phoneNumber;

  const OtpVerifyScreen({super.key, required this.phoneNumber});

  @override
  ConsumerState<OtpVerifyScreen> createState() => _OtpVerifyScreenState();
}

class _OtpVerifyScreenState extends ConsumerState<OtpVerifyScreen> {
  String _code = '';
  String? _error;

  void _onDigit(String digit) {
    if (_code.length >= 6) return;
    setState(() => _code += digit);
  }

  void _onBackspace() {
    if (_code.isEmpty) return;
    setState(() => _code = _code.substring(0, _code.length - 1));
  }

  Future<void> _verify() async {
    try {
      await ref.read(authRepositoryProvider).verifyOtp(widget.phoneNumber, _code);
      if (!mounted) return;
      Navigator.of(context).pushNamedAndRemoveUntil('/unit-selection', (route) => false);
    } catch (e) {
      setState(() => _error = '인증번호가 올바르지 않습니다.');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('인증번호 입력')),
      body: Column(
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 24, 20, 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('문자로 받은 번호\n6자리를 입력해주세요',
                  style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.black)),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              children: List.generate(6, (i) {
                final filled = i < _code.length;
                return Expanded(
                  child: Container(
                    margin: const EdgeInsets.symmetric(horizontal: 4),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    decoration: BoxDecoration(
                      border: Border.all(color: filled ? const Color(0xFF2E9E4F) : Colors.grey, width: 2),
                    ),
                    alignment: Alignment.center,
                    child: Text(filled ? _code[i] : '',
                        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
                  ),
                );
              }),
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: AtmNumericKeypad(
              onDigit: _onDigit,
              onBackspace: _onBackspace,
              onConfirm: _code.length == 6 ? _verify : null,
              confirmLabel: '확인',
            ),
          ),
          const Spacer(),
          AtmBottomActionBar.single(label: '이전', onPressed: () => Navigator.of(context).maybePop()),
        ],
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/auth/otp_verify_screen_test.dart`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/auth/otp_verify_screen.dart mobile/test/features/auth/otp_verify_screen_test.dart
git commit -m "refactor(otp-verify): use AtmNumericKeypad and AtmBottomActionBar"
```

---

## Task 10: unit_selection_screen 리팩토링

**Files:**
- Modify: `mobile/lib/features/unit_selection/unit_selection_screen.dart`
- Modify: `mobile/test/features/unit_selection/unit_selection_screen_test.dart`

**Interfaces:**
- Consumes: `AtmOptionListItem` (Task 4), `AtmBottomActionBar.single` (Task 3)

- [ ] **Step 1: Write the failing test**

`unit_selection_screen_test.dart` 파일 끝(마지막 `}` 앞)에 아래 테스트를 추가한다.

```dart
  testWidgets('질문 문구와 이전 버튼이 보인다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: UnitSelectionScreen()));

    expect(find.text('사업단을 선택해주세요'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, '이전'), findsOneWidget);
  });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/unit_selection/unit_selection_screen_test.dart`
Expected: FAIL — `find.text('사업단을 선택해주세요')` 매칭 위젯 없음

- [ ] **Step 3: Write minimal implementation**

`unit_selection_screen.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import '../../core/unit_type.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_option_list_item.dart';
import '../job_search/job_search_screen.dart';

class UnitSelectionScreen extends StatelessWidget {
  const UnitSelectionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('사업단 유형 선택')),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 24, 20, 12),
            child: Text('사업단을 선택해주세요',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.black)),
          ),
          ...UnitType.values.map((type) {
            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
              child: AtmOptionListItem(
                title: type.label,
                onTap: () {
                  Navigator.of(context).push(MaterialPageRoute(
                    builder: (_) => JobSearchScreen(unitType: type),
                  ));
                },
              ),
            );
          }),
        ],
      ),
      bottomNavigationBar: AtmBottomActionBar.single(
        label: '이전',
        onPressed: () => Navigator.of(context).maybePop(),
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/unit_selection/unit_selection_screen_test.dart`
Expected: PASS (3 tests: 기존 2 + 신규 1)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/unit_selection/unit_selection_screen.dart mobile/test/features/unit_selection/unit_selection_screen_test.dart
git commit -m "refactor(unit-selection): use AtmOptionListItem and AtmBottomActionBar"
```

---

## Task 11: job_search_screen 리팩토링

**Files:**
- Modify: `mobile/lib/features/job_search/job_search_screen.dart`
- Modify: `mobile/test/features/job_search/job_search_screen_test.dart`

**Interfaces:**
- Consumes: `AtmOptionListItem` (Task 4), `AtmBottomActionBar.single` (Task 3)

- [ ] **Step 1: Write the failing test**

`job_search_screen_test.dart` 파일 끝(마지막 `}` 앞)에 아래 테스트를 추가한다.

```dart
  testWidgets('질문 문구와 이전 버튼이 보인다', (tester) async {
    await tester.pumpWidget(_wrap(
      const JobSearchScreen(unitType: UnitType.publicInterest),
      (options) async => jsonResponse('[]'),
    ));
    await tester.pumpAndSettle();

    expect(find.text('어떤 일을 하시나요?'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, '이전'), findsOneWidget);
  });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/job_search/job_search_screen_test.dart`
Expected: FAIL — `find.text('어떤 일을 하시나요?')` 매칭 위젯 없음

- [ ] **Step 3: Write minimal implementation**

`job_search_screen.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/unit_type.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_option_list_item.dart';
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
            padding: EdgeInsets.fromLTRB(16, 20, 16, 8),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('어떤 일을 하시나요?',
                  style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: Colors.black)),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
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
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
                  child: AtmOptionListItem(
                    title: place.name,
                    subtitle: place.address,
                    onTap: _loading ? null : () => _select(place),
                  ),
                );
              },
            ),
          ),
        ],
      ),
      bottomNavigationBar: AtmBottomActionBar.single(
        label: '이전',
        onPressed: () => Navigator.of(context).maybePop(),
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/job_search/job_search_screen_test.dart`
Expected: PASS (6 tests: 기존 5 + 신규 1)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/job_search/job_search_screen.dart mobile/test/features/job_search/job_search_screen_test.dart
git commit -m "refactor(job-search): use AtmOptionListItem and AtmBottomActionBar"
```

---

## Task 12: consent_screen 리팩토링

**Files:**
- Modify: `mobile/lib/features/consent/consent_screen.dart`
- Modify: `mobile/test/features/consent/consent_screen_test.dart`

**Interfaces:**
- Consumes: `AtmBottomActionBar.confirm` (Task 3), `ConsentRepository.agree()` (기존, 변경 없음)

- [ ] **Step 1: Write the failing test**

`consent_screen_test.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/consent/consent_screen.dart';

import '../../support/fake_api_client.dart';

void main() {
  testWidgets('네를 누르면 동의 후 출석 체크 화면으로 이동한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}'))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('출석 체크'), findsNWidgets(2));
  });

  testWidgets('제출 중 서버 오류가 나면 에러 안내를 보여주고 화면을 유지한다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async => jsonResponse('{}', statusCode: 500))),
      ],
      child: const MaterialApp(home: ConsentScreen()),
    ));

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('동의 처리에 실패했습니다. 다시 시도해주세요.'), findsOneWidget);
    expect(find.text('위치정보 수집 동의'), findsOneWidget);
  });

  testWidgets('아니오를 누르면 이전 화면으로 돌아간다', (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => ElevatedButton(
          onPressed: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const ConsentScreen()),
          ),
          child: const Text('열기'),
        ),
      ),
    ));

    await tester.tap(find.text('열기'));
    await tester.pumpAndSettle();
    expect(find.text('위치정보 수집 동의'), findsOneWidget);

    await tester.tap(find.widgetWithText(ElevatedButton, '아니오'));
    await tester.pumpAndSettle();

    expect(find.text('위치정보 수집 동의'), findsNothing);
    expect(find.text('열기'), findsOneWidget);
  });

  testWidgets('자세히 보기를 누르면 전문 약관 다이얼로그가 뜬다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: ConsentScreen()));

    await tester.tap(find.text('자세히 보기'));
    await tester.pumpAndSettle();

    expect(find.text('위치정보 수집 약관'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/consent/consent_screen_test.dart`
Expected: FAIL — `find.widgetWithText(ElevatedButton, '네')` 매칭 위젯 없음 (기존 화면은 체크박스+"동의하고 계속하기" 버튼 구조)

- [ ] **Step 3: Write minimal implementation**

`consent_screen.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../auth/auth_provider.dart';
import '../checkin/checkin_screen.dart';
import 'consent_repository.dart';

class ConsentScreen extends ConsumerStatefulWidget {
  const ConsentScreen({super.key});

  @override
  ConsumerState<ConsentScreen> createState() => _ConsentScreenState();
}

class _ConsentScreenState extends ConsumerState<ConsentScreen> {
  bool _loading = false;
  String? _error;

  static const _fullTerms =
      '출석 체크를 위해 체크인 시점의 위치정보(GPS 좌표)를 수집합니다. '
      '수집된 위치정보는 출석 확인 목적으로만 사용되며, 최초 1회 동의로 이후 출석 체크에 계속 적용됩니다.';

  Future<void> _agree() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ConsentRepository(dio: ref.read(apiClientProvider).dio);
      await repo.agree();
      if (!mounted) return;
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => const CheckinScreen()));
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = '동의 처리에 실패했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _decline() {
    Navigator.of(context).maybePop();
  }

  void _showFullTerms() {
    showDialog<void>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('위치정보 수집 약관'),
        content: const SingleChildScrollView(child: Text(_fullTerms)),
        actions: [TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('닫기'))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('위치정보 수집 동의')),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.fromLTRB(20, 24, 20, 12),
            child: Text('위치정보 수집에\n동의하시겠어요?',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.black)),
          ),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 20),
            child: Text(
              '출석 체크 시점의 위치(GPS)를 수집하며, 출석 확인 목적으로만 사용됩니다. 최초 1회만 동의하면 됩니다.',
              style: TextStyle(fontSize: 15, color: Colors.black54),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
            child: GestureDetector(
              onTap: _showFullTerms,
              child: const Text('자세히 보기',
                  style: TextStyle(
                      fontSize: 14,
                      color: Color(0xFF2E9E4F),
                      decoration: TextDecoration.underline,
                      fontWeight: FontWeight.bold)),
            ),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Text(_error!, style: const TextStyle(color: Colors.red)),
            ),
          if (_loading) const Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator()),
        ],
      ),
      bottomNavigationBar: AtmBottomActionBar.confirm(
        onYes: _loading ? null : _agree,
        onNo: _decline,
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/consent/consent_screen_test.dart`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/consent/consent_screen.dart mobile/test/features/consent/consent_screen_test.dart
git commit -m "refactor(consent): use summary text and AtmBottomActionBar confirm pattern"
```

---

## Task 13: checkin_repository — 오늘 일정 조회 연동

**Files:**
- Modify: `mobile/lib/features/checkin/checkin_repository.dart`
- Modify: `mobile/test/features/checkin/checkin_repository_test.dart`

**Interfaces:**
- Consumes: 백엔드 `GET /api/v1/attend/today` 응답 형식 `{hasSchedule, scheduleId?, placeName?, startTime?, endTime?}` (Task 7)
- Produces: `TodayAttend`(`hasSchedule`, `scheduleId`, `placeName`, `startTime`, `endTime`) 모델, `CheckinRepository.getTodayAttend()` → `Future<TodayAttend>`

- [ ] **Step 1: Write the failing test**

`checkin_repository_test.dart` 파일 끝(마지막 `}` 앞, `_FakeAdapter` 클래스 앞)에 아래 테스트를 추가한다.

```dart
  test('getTodayAttend parses hasSchedule=true response', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter(
      '{"hasSchedule":true,"scheduleId":12,"placeName":"중앙공원","startTime":"09:00","endTime":"13:00"}',
    );
    final repository = CheckinRepository(dio: dio);

    final today = await repository.getTodayAttend();

    expect(today.hasSchedule, isTrue);
    expect(today.scheduleId, 12);
    expect(today.placeName, '중앙공원');
    expect(today.startTime, '09:00');
    expect(today.endTime, '13:00');
  });

  test('getTodayAttend parses hasSchedule=false response', () async {
    final dio = Dio();
    dio.httpClientAdapter = _FakeAdapter('{"hasSchedule":false}');
    final repository = CheckinRepository(dio: dio);

    final today = await repository.getTodayAttend();

    expect(today.hasSchedule, isFalse);
    expect(today.scheduleId, isNull);
  });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/checkin/checkin_repository_test.dart`
Expected: FAIL — `The method 'getTodayAttend' isn't defined for the type 'CheckinRepository'`

- [ ] **Step 3: Write minimal implementation**

`checkin_repository.dart` 전체를 아래로 교체한다.

```dart
import 'package:dio/dio.dart';

class TodayAttend {
  final bool hasSchedule;
  final int? scheduleId;
  final String? placeName;
  final String? startTime;
  final String? endTime;

  TodayAttend({
    required this.hasSchedule,
    this.scheduleId,
    this.placeName,
    this.startTime,
    this.endTime,
  });

  factory TodayAttend.fromJson(Map<String, dynamic> json) {
    return TodayAttend(
      hasSchedule: json['hasSchedule'] as bool? ?? false,
      scheduleId: json['scheduleId'] as int?,
      placeName: json['placeName'] as String?,
      startTime: json['startTime'] as String?,
      endTime: json['endTime'] as String?,
    );
  }
}

class CheckinResult {
  final bool success;
  final String message;

  CheckinResult({required this.success, required this.message});
}

class CheckinRepository {
  final Dio dio;

  CheckinRepository({required this.dio});

  Future<TodayAttend> getTodayAttend() async {
    final response = await dio.get('/api/v1/attend/today');
    return TodayAttend.fromJson(response.data as Map<String, dynamic>);
  }

  Future<CheckinResult> checkIn({
    required int scheduleId,
    required double latitude,
    required double longitude,
  }) async {
    try {
      final response = await dio.post('/api/v1/attend/check-in', data: {
        'scheduleId': scheduleId,
        'latitude': latitude,
        'longitude': longitude,
      });
      return CheckinResult(
        success: response.data['success'] as bool? ?? true,
        message: response.data['message'] as String? ?? '출석 처리되었습니다.',
      );
    } on DioException catch (e) {
      final message = e.response?.data is Map
          ? (e.response?.data['message'] as String? ?? '출석 처리에 실패했습니다.')
          : '출석 처리에 실패했습니다.';
      return CheckinResult(success: false, message: message);
    }
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/checkin/checkin_repository_test.dart`
Expected: PASS (4 tests: 기존 2 + 신규 2)

- [ ] **Step 5: Commit**

```bash
git add mobile/lib/features/checkin/checkin_repository.dart mobile/test/features/checkin/checkin_repository_test.dart
git commit -m "feat(checkin-repository): add getTodayAttend"
```

---

## Task 14: checkin_screen 리팩토링

**Files:**
- Modify: `mobile/lib/features/checkin/checkin_screen.dart`
- Modify: `mobile/test/features/checkin/checkin_screen_test.dart`

**Interfaces:**
- Consumes: `TodayAttend`, `CheckinRepository.getTodayAttend()`, `CheckinRepository.checkIn()` (Task 13), `AtmBottomActionBar.confirm`/`.single` (Task 3)

- [ ] **Step 1: Write the failing test**

`checkin_screen_test.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:geolocator_platform_interface/geolocator_platform_interface.dart';
import 'package:senior_job_attendance/features/auth/auth_provider.dart';
import 'package:senior_job_attendance/features/checkin/checkin_screen.dart';

import '../../support/fake_api_client.dart';

class _FakeGeolocatorPlatform extends GeolocatorPlatform {
  final LocationPermission permission;
  final Position? position;
  final bool throwOnPosition;

  _FakeGeolocatorPlatform({required this.permission, this.position, this.throwOnPosition = false});

  @override
  Future<LocationPermission> requestPermission() async => permission;

  @override
  Future<Position> getCurrentPosition({LocationSettings? locationSettings}) async {
    if (throwOnPosition) {
      throw const LocationServiceDisabledException();
    }
    return position!;
  }
}

Position _fakePosition() {
  return Position(
    latitude: 35.3,
    longitude: 129.0,
    timestamp: DateTime.now(),
    accuracy: 1.0,
    altitude: 0.0,
    altitudeAccuracy: 1.0,
    heading: 0.0,
    headingAccuracy: 1.0,
    speed: 0.0,
    speedAccuracy: 1.0,
  );
}

void main() {
  testWidgets('오늘 일정이 없으면 안내 문구를 보여주고 네/아니오 버튼이 비활성화된다', (tester) async {
    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          return jsonResponse('{"hasSchedule":false}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    expect(find.text('오늘은 예정된 출석이 없습니다'), findsOneWidget);
    final yesButton = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '네'));
    final noButton = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, '아니오'));
    expect(yesButton.onPressed, isNull);
    expect(noButton.onPressed, isNull);
  });

  testWidgets('위치 권한을 거부하면 안내 메시지를 보여주고 서버를 호출하지 않는다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(permission: LocationPermission.denied);
    bool checkInCalled = false;

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/check-in') {
            checkInCalled = true;
          }
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.'), findsOneWidget);
    expect(checkInCalled, isFalse);
  });

  testWidgets('위치 확인과 체크인이 성공하면 결과 화면에 서버 응답 메시지를 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      position: _fakePosition(),
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          return jsonResponse('{"success":true,"message":"출석 처리되었습니다."}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('출석 처리되었습니다.'), findsOneWidget);
    expect(find.widgetWithText(ElevatedButton, '확인'), findsOneWidget);
  });

  testWidgets('위치 서비스가 꺼져 있으면 위치 확인 실패 안내를 보여준다', (tester) async {
    GeolocatorPlatform.instance = _FakeGeolocatorPlatform(
      permission: LocationPermission.whileInUse,
      throwOnPosition: true,
    );

    await tester.pumpWidget(ProviderScope(
      overrides: [
        apiClientProvider.overrideWithValue(fakeApiClient((options) async {
          if (options.path == '/api/v1/attend/today') {
            return jsonResponse('{"hasSchedule":true,"scheduleId":1,"placeName":"중앙공원"}');
          }
          return jsonResponse('{}');
        })),
      ],
      child: const MaterialApp(home: CheckinScreen()),
    ));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(ElevatedButton, '네'));
    await tester.pumpAndSettle();

    expect(find.text('위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.'), findsOneWidget);
  });
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd mobile && flutter test test/features/checkin/checkin_screen_test.dart`
Expected: FAIL — `find.widgetWithText(ElevatedButton, '네')` 매칭 위젯 없음 (기존 화면은 버튼 1개 구조)

- [ ] **Step 3: Write minimal implementation**

`checkin_screen.dart` 전체를 아래로 교체한다.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../auth/auth_provider.dart';
import 'checkin_repository.dart';

class CheckinScreen extends ConsumerStatefulWidget {
  const CheckinScreen({super.key});

  @override
  ConsumerState<CheckinScreen> createState() => _CheckinScreenState();
}

class _CheckinScreenState extends ConsumerState<CheckinScreen> {
  bool _loadingToday = true;
  TodayAttend? _today;
  bool _checkingIn = false;
  String? _errorMessage;
  CheckinResult? _result;

  @override
  void initState() {
    super.initState();
    _loadToday();
  }

  Future<void> _loadToday() async {
    setState(() => _loadingToday = true);
    try {
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final today = await repo.getTodayAttend();
      if (!mounted) return;
      setState(() => _today = today);
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorMessage = '오늘 일정을 불러오지 못했습니다. 다시 시도해주세요.');
    } finally {
      if (mounted) setState(() => _loadingToday = false);
    }
  }

  Future<void> _confirmCheckIn() async {
    final scheduleId = _today?.scheduleId;
    if (scheduleId == null || _checkingIn) return;

    setState(() {
      _checkingIn = true;
      _errorMessage = null;
    });
    try {
      final permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        if (!mounted) return;
        setState(() => _errorMessage = '위치 권한이 필요합니다. 설정에서 위치 권한을 허용해주세요.');
        return;
      }

      final position = await Geolocator.getCurrentPosition();
      final repo = CheckinRepository(dio: ref.read(apiClientProvider).dio);
      final result = await repo.checkIn(
        scheduleId: scheduleId,
        latitude: position.latitude,
        longitude: position.longitude,
      );
      if (!mounted) return;
      setState(() => _result = result);
    } catch (e) {
      if (mounted) {
        setState(() => _errorMessage = '위치 확인에 실패했습니다. 위치 서비스가 켜져 있는지 확인해주세요.');
      }
    } finally {
      if (mounted) setState(() => _checkingIn = false);
    }
  }

  void _declineCheckIn() {
    Navigator.of(context).maybePop();
  }

  @override
  Widget build(BuildContext context) {
    if (_result != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('출석 체크')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(_result!.success ? Icons.check_circle : Icons.info,
                  color: _result!.success ? const Color(0xFF2E9E4F) : Colors.grey, size: 40),
              const SizedBox(height: 10),
              Text(
                _result!.message,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: Colors.black),
              ),
            ],
          ),
        ),
        bottomNavigationBar: AtmBottomActionBar.single(
          label: '확인',
          onPressed: () => Navigator.of(context).popUntil((route) => route.isFirst),
        ),
      );
    }

    final canAct = _today != null && _today!.hasSchedule;

    return Scaffold(
      appBar: AppBar(title: const Text('출석 체크')),
      body: _loadingToday
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.fromLTRB(20, 24, 20, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('지금 출석 체크를\n하시겠어요?',
                      style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: Colors.black)),
                  const SizedBox(height: 20),
                  if (canAct)
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      decoration: BoxDecoration(border: Border.all(color: Colors.grey.shade300)),
                      child: Text(
                          '오늘 일정: ${_today!.placeName ?? ''} ${_today!.startTime ?? ''}~${_today!.endTime ?? ''}'),
                    )
                  else
                    const Text('오늘은 예정된 출석이 없습니다', style: TextStyle(color: Colors.black54)),
                  if (_errorMessage != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 16),
                      child: Text(_errorMessage!, style: const TextStyle(color: Colors.red)),
                    ),
                ],
              ),
            ),
      bottomNavigationBar: AtmBottomActionBar.confirm(
        onYes: (canAct && !_checkingIn) ? _confirmCheckIn : null,
        onNo: canAct ? _declineCheckIn : null,
      ),
    );
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd mobile && flutter test test/features/checkin/checkin_screen_test.dart`
Expected: PASS (4 tests)

- [ ] **Step 5: Run the full mobile test suite**

Run: `cd mobile && flutter test`
Expected: PASS (모든 파일 통과 — 신규 5 design_system 파일 + 6개 화면 파일 + 기존 repository 테스트)

- [ ] **Step 6: Run the full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: PASS (모든 기존 테스트 + `AttendServiceTest`/`AttendControllerIntegrationTest` 신규 케이스)

- [ ] **Step 7: Commit**

```bash
git add mobile/lib/features/checkin/checkin_screen.dart mobile/test/features/checkin/checkin_screen_test.dart
git commit -m "refactor(checkin): fetch today's schedule and use confirm/result flow"
```

---

## Self-Review Notes

- **Spec coverage:** Task 1–5는 스펙의 "공용 위젯" 표를 1:1로 구현. Task 8–12는 스펙의 "화면별 통합 매핑" 표를 그대로 반영. Task 6–7은 스펙의 "백엔드 — 오늘 일정 조회 API" 섹션을 구현. Task 13–14는 체크인 화면의 하드코딩 제거와 "오늘 일정 없음" 에러 케이스(스펙의 에러 처리 표)를 구현. 스펙의 모든 섹션에 대응하는 태스크가 존재한다.
- **Placeholder scan:** 전 태스크에 TBD/TODO 없음. 모든 코드 블록은 실제 실행 가능한 전체 내용이다.
- **Type consistency:** `TodayAttend`(Task 13)의 필드명(`hasSchedule`/`scheduleId`/`placeName`/`startTime`/`endTime`)이 `AttendTodayResponse`(Task 6) JSON 필드명과 정확히 일치한다. `AtmBottomActionBar.confirm`(Task 3)의 `onYes`/`onNo` 파라미터명이 Task 12(`consent_screen`)·Task 14(`checkin_screen`) 호출부와 일치한다. `AtmNumericKeypad`(Task 5)의 `onConfirm`/`confirmLabel`이 Task 8·9의 호출부와 일치한다.
