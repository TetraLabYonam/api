import 'package:flutter/material.dart';
import '../../core/unit_type.dart';
import '../../design_system/atm_bottom_action_bar.dart';
import '../../design_system/atm_colors.dart';
import '../job_search/job_search_screen.dart';

IconData _iconFor(UnitType type) {
  switch (type) {
    case UnitType.publicInterest:
      return Icons.groups;
    case UnitType.market:
      return Icons.storefront;
    case UnitType.socialService:
      return Icons.volunteer_activism;
  }
}

class UnitSelectionScreen extends StatelessWidget {
  const UnitSelectionScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('ATTENDANCE')),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(24, 20, 24, 20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('일자리 유형을\n선택해주세요',
                        style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold, color: AtmColors.primary, height: 1.3)),
                    const SizedBox(height: 8),
                    const Text('어르신에게 가장 잘 맞는 일자리 방식을 선택해 보세요.',
                        style: TextStyle(fontSize: 16, color: AtmColors.onSurfaceVariant)),
                    const SizedBox(height: 20),
                    ...UnitType.values.map((type) {
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 16),
                        child: _UnitTypeCard(
                          type: type,
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
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 20),
              child: AtmBottomActionBar.single(
                label: '이전',
                onPressed: () => Navigator.of(context).maybePop(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _UnitTypeCard extends StatelessWidget {
  final UnitType type;
  final VoidCallback onTap;

  const _UnitTypeCard({required this.type, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          border: Border.all(color: AtmColors.border, width: 2),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(_iconFor(type), size: 28, color: AtmColors.primary),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(type.label,
                      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: AtmColors.primary)),
                  const SizedBox(height: 4),
                  Text(type.description,
                      style: const TextStyle(fontSize: 16, color: AtmColors.onSurfaceVariant)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
