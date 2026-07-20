import 'package:flutter/material.dart';
import 'atm_colors.dart';
import 'atm_primary_button.dart';

class AtmNumericKeypad extends StatelessWidget {
  final ValueChanged<String> onDigit;
  final VoidCallback onBackspace;
  final VoidCallback? onConfirm;
  final String confirmLabel;

  const AtmNumericKeypad({
    super.key,
    required this.onDigit,
    required this.onBackspace,
    required this.onConfirm,
    this.confirmLabel = '확인',
  });

  Widget _digitKey(String label, VoidCallback onTap) {
    return OutlinedButton(
      onPressed: onTap,
      style: OutlinedButton.styleFrom(
        backgroundColor: AtmColors.onPrimary,
        foregroundColor: AtmColors.primary,
        side: const BorderSide(color: AtmColors.border, width: 2),
        minimumSize: const Size.fromHeight(64),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
      child: Text(label, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
    );
  }

  Widget _backspaceKey() {
    return OutlinedButton(
      onPressed: onBackspace,
      style: OutlinedButton.styleFrom(
        backgroundColor: AtmColors.surface,
        foregroundColor: AtmColors.primary,
        side: const BorderSide(color: AtmColors.border, width: 2),
        minimumSize: const Size.fromHeight(64),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
      child: const Icon(Icons.backspace_outlined, size: 24),
    );
  }

  @override
  Widget build(BuildContext context) {
    Widget row(List<Widget> children) => Row(
          children: children
              .map((w) => Expanded(child: Padding(padding: const EdgeInsets.all(6), child: w)))
              .toList(),
        );

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        row([
          _digitKey('1', () => onDigit('1')),
          _digitKey('2', () => onDigit('2')),
          _digitKey('3', () => onDigit('3')),
        ]),
        row([
          _digitKey('4', () => onDigit('4')),
          _digitKey('5', () => onDigit('5')),
          _digitKey('6', () => onDigit('6')),
        ]),
        row([
          _digitKey('7', () => onDigit('7')),
          _digitKey('8', () => onDigit('8')),
          _digitKey('9', () => onDigit('9')),
        ]),
        row([
          const SizedBox.shrink(),
          _digitKey('0', () => onDigit('0')),
          _backspaceKey(),
        ]),
        const SizedBox(height: 12),
        AtmPrimaryButton(label: confirmLabel, onPressed: onConfirm),
      ],
    );
  }
}
