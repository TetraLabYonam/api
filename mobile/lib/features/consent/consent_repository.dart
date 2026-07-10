import 'package:dio/dio.dart';

class ConsentRepository {
  final Dio dio;

  ConsentRepository({required this.dio});

  Future<void> agree() async {
    await dio.post('/api/v1/members/me/consent');
  }
}
