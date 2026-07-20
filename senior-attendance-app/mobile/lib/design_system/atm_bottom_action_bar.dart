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

  ButtonStyle _fillStyle(Color background) {
    return ElevatedButton.styleFrom(
      backgroundColor: background,
      foregroundColor: AtmColors.onPrimary,
      minimumSize: const Size.fromHeight(64),
      shape: const RoundedRectangleBorder(),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (!_isConfirm) {
      return SizedBox(
        width: double.infinity,
        child: OutlinedButton(
          onPressed: onSingleTap,
          style: OutlinedButton.styleFrom(
            backgroundColor: AtmColors.onPrimary,
            foregroundColor: AtmColors.primary,
            side: const BorderSide(color: AtmColors.border, width: 2),
            minimumSize: const Size.fromHeight(64),
            shape: const RoundedRectangleBorder(),
          ),
          child: Text(singleLabel!, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        ),
      );
    }
    return Row(
      children: [
        Expanded(
          child: ElevatedButton(
            onPressed: onYesTap,
            style: _fillStyle(AtmColors.success),
            child: const Text('네', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
        ),
        Expanded(
          child: ElevatedButton(
            onPressed: onNoTap,
            style: _fillStyle(AtmColors.error),
            child: const Text('아니오', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
        ),
      ],
    );
  }
}
