import 'package:flutter/material.dart';
import '../../core/unit_type.dart';
import '../job_search/job_search_screen.dart';

class UnitSelectionScreen extends StatelessWidget {
  const UnitSelectionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('사업단 유형 선택')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: UnitType.values.map((type) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: ElevatedButton(
              style: ElevatedButton.styleFrom(minimumSize: const Size.fromHeight(64)),
              onPressed: () {
                Navigator.of(context).push(MaterialPageRoute(
                  builder: (_) => JobSearchScreen(unitType: type),
                ));
              },
              child: Text(type.label, style: const TextStyle(fontSize: 20)),
            ),
          );
        }).toList(),
      ),
    );
  }
}
