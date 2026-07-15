import 'package:flutter/material.dart';
import 'atm_colors.dart';

class AtmSecondaryButton extends StatelessWidget {
  final String label;
  final VoidCallback? onPressed;

  const AtmSecondaryButton({super.key, required this.label, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: ElevatedButton(
        onPressed: onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: AtmColors.secondary,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(72),
          shape: const RoundedRectangleBorder(borderRadius: BorderRadius.all(Radius.circular(2))),
        ),
        child: Text(label, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
      ),
    );
  }
}
