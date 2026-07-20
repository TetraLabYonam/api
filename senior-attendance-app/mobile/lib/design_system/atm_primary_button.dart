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
          foregroundColor: AtmColors.onPrimary,
          disabledBackgroundColor: AtmColors.disabled,
          disabledForegroundColor: AtmColors.onDisabled,
          minimumSize: const Size.fromHeight(64),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: Text(label, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
      ),
    );
  }
}
