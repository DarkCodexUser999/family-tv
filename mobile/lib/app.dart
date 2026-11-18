import 'package:material_ui/material_ui.dart';

import 'features/pairing/pairing_controller.dart';
import 'features/pairing/pairing_screen.dart';

class FamilyTvApp extends StatefulWidget {
  const FamilyTvApp({super.key, required this.controller});
  final PairingController controller;
  @override
  State<FamilyTvApp> createState() => _FamilyTvAppState();
}

class _FamilyTvAppState extends State<FamilyTvApp> {
  @override
  void dispose() {
    widget.controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Family TV Remote',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      colorScheme: ColorScheme.fromSeed(
        seedColor: const Color(0xff8ac7ff),
        brightness: Brightness.dark,
      ),
      scaffoldBackgroundColor: const Color(0xff10151e),
      useMaterial3: true,
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(minimumSize: const Size(48, 56)),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(minimumSize: const Size(48, 56)),
      ),
    ),
    home: PairingScreen(controller: widget.controller),
  );
}
