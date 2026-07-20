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
        constraints: const BoxConstraints(minHeight: 80),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
        decoration: const BoxDecoration(
          color: AtmColors.onPrimary,
          border: Border(bottom: BorderSide(color: AtmColors.border, width: 1)),
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(title, style: const TextStyle(color: AtmColors.primary, fontSize: 20, fontWeight: FontWeight.bold)),
                  if (subtitle != null) ...[
                    const SizedBox(height: 4),
                    Text(subtitle!, style: const TextStyle(color: AtmColors.onSurfaceVariant, fontSize: 16)),
                  ],
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AtmColors.primary, size: 28),
          ],
        ),
      ),
    );
  }
}
