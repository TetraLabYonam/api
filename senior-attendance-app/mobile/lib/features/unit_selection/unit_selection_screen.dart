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
