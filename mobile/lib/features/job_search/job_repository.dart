import 'package:dio/dio.dart';
import '../../core/unit_type.dart';

class PlaceSummary {
  final int id;
  final String name;
  final String address;
  final String? description;

  PlaceSummary({required this.id, required this.name, required this.address, this.description});

  factory PlaceSummary.fromJson(Map<String, dynamic> json) {
    return PlaceSummary(
      id: json['id'] as int,
      name: json['name'] as String,
      address: json['address'] as String,
      description: json['description'] as String?,
    );
  }
}

class JobRepository {
  final Dio dio;

  JobRepository({required this.dio});

  Future<List<PlaceSummary>> list(UnitType unitType) async {
    final response = await dio.get('/api/v1/places', queryParameters: {'unitType': unitType.apiValue});
    return _parseList(response.data);
  }

  Future<List<PlaceSummary>> search(UnitType unitType, String q) async {
    final response = await dio.get('/api/v1/places', queryParameters: {
      'unitType': unitType.apiValue,
      'q': q,
    });
    return _parseList(response.data);
  }

  Future<List<PlaceSummary>> searchFallback(UnitType unitType, String q) async {
    final response = await dio.post('/api/v1/places/search/fallback', data: {
      'unitType': unitType.apiValue,
      'q': q,
    });
    return _parseList(response.data);
  }

  Future<void> assignPlace(int placeId) async {
    await dio.post('/api/v1/members/me/assign-place', data: {'placeId': placeId});
  }

  List<PlaceSummary> _parseList(dynamic data) {
    return (data as List).map((e) => PlaceSummary.fromJson(e as Map<String, dynamic>)).toList();
  }
}
