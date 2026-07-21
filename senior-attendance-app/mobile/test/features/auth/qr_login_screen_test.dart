import 'package:flutter_test/flutter_test.dart';
import 'package:senior_job_attendance/features/auth/qr_login_screen.dart';

void main() {
  test('정상 포맷 파싱', () {
    final result = parseQrPayload('1001:01012345678');
    expect(result?.employeeId, 1001);
    expect(result?.phoneNumber, '01012345678');
  });
  test('콜론 없으면 null', () {
    expect(parseQrPayload('invalid'), isNull);
  });
  test('직번이 숫자가 아니면 null', () {
    expect(parseQrPayload('abc:01012345678'), isNull);
  });
}
