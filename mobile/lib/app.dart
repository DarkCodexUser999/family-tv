import 'package:material_ui/material_ui.dart';

class FamilyTvApp extends StatelessWidget {
  const FamilyTvApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Family TV Remote', debugShowCheckedModeBanner: false,
    theme: ThemeData(colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff8ac7ff), brightness: Brightness.dark),
      scaffoldBackgroundColor: const Color(0xff10151e), useMaterial3: true),
    home: Scaffold(appBar: AppBar(title: const Text('Family TV Remote')),
      body: const SafeArea(child: Padding(padding: EdgeInsets.all(24),
        child: Text('Secure home Wi-Fi pairing')))),
  );
}
