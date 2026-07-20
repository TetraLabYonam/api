import 'package:flutter/material.dart';
import 'atm_colors.dart';

/// 중립 보조 액션(취소, 뒤로가기 등)에 쓰는 아웃라인 버튼.
class AtmSecondaryButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;

  const AtmSecondaryButton({super.key, required this.label, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton(
        onPressed: onPressed,
        style: OutlinedButton.styleFrom(
          backgroundColor: AtmColors.onPrimary,
          foregroundColor: AtmColors.primary,
          side: const BorderSide(color: AtmColors.border, width: 2),
          minimumSize: const Size.fromHeight(64),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: Text(label, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
      ),
    );
  }
}
