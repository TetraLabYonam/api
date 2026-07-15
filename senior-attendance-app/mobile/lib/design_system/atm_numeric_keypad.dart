import 'package:flutter/material.dart';
import 'atm_colors.dart';

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

  Widget _key(String label, {VoidCallback? onTap, Color? background, Color? foreground}) {
    return ElevatedButton(
      onPressed: onTap,
      style: ElevatedButton.styleFrom(
        backgroundColor: background,
        foregroundColor: foreground ?? Colors.black87,
        minimumSize: const Size.fromHeight(56),
        shape: const RoundedRectangleBorder(),
      ),
      child: Text(label, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
    );
  }

  @override
  Widget build(BuildContext context) {
    Widget row(List<Widget> children) => Row(
          children: children
              .map((w) => Expanded(child: Padding(padding: const EdgeInsets.all(3), child: w)))
              .toList(),
        );

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        row([
          _key('1', onTap: () => onDigit('1')),
          _key('2', onTap: () => onDigit('2')),
          _key('3', onTap: () => onDigit('3')),
        ]),
        row([
          _key('4', onTap: () => onDigit('4')),
          _key('5', onTap: () => onDigit('5')),
          _key('6', onTap: () => onDigit('6')),
        ]),
        row([
          _key('7', onTap: () => onDigit('7')),
          _key('8', onTap: () => onDigit('8')),
          _key('9', onTap: () => onDigit('9')),
        ]),
        row([
          _key('지우기', onTap: onBackspace, background: AtmColors.secondary, foreground: Colors.white),
          _key('0', onTap: () => onDigit('0')),
          _key(confirmLabel, onTap: onConfirm, background: AtmColors.primary, foreground: Colors.white),
        ]),
      ],
    );
  }
}
