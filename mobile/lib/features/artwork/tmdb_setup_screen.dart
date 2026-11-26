import 'package:material_ui/material_ui.dart';

import '../../core/api/models.dart';
import 'tmdb_settings.dart';

class TmdbSetupScreen extends StatefulWidget {
  const TmdbSetupScreen({super.key});
  @override
  State<TmdbSetupScreen> createState() => _TmdbSetupScreenState();
}

class _TmdbSetupScreenState extends State<TmdbSetupScreen> {
  final input = TextEditingController();
  final store = const TmdbSettings();
  bool busy = false;
  String message =
      'Each phone needs its own token. Never paste it into a chat or GitHub.';
  Future<void> change(bool remove) async {
    setState(() => busy = true);
    try {
      if (remove) {
        await store.remove();
      } else {
        await store.save(input.text.trim());
      }
      input.clear();
      if (mounted) {
        setState(
          () => message = remove
              ? 'Token removed from this phone.'
              : 'Token saved securely on this phone.',
        );
      }
    } catch (e) {
      if (mounted) {
        setState(
          () => message = e is AppFailure
              ? e.message
              : 'Setup could not be saved.',
        );
      }
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  void dispose() {
    input.clear();
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Family TV · TMDB setup')),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const Text('Optional movie artwork', style: TextStyle(fontSize: 24)),
        const Text(
          'Create your own API Read Access Token in TMDB account settings. This phone fetches artwork over verified HTTPS; your token never goes to the TV.',
        ),
        const SelectableText('https://www.themoviedb.org/settings/api'),
        TextField(
          controller: input,
          obscureText: true,
          autocorrect: false,
          enableSuggestions: false,
          maxLength: 2048,
          enabled: !busy,
          decoration: const InputDecoration(labelText: 'API Read Access Token'),
        ),
        FilledButton(
          onPressed: busy ? null : () => change(false),
          child: const Text('Save securely'),
        ),
        OutlinedButton(
          onPressed: busy ? null : () => change(true),
          child: const Text('Remove phone token'),
        ),
        Semantics(liveRegion: true, child: Text(message)),
        const Divider(),
        Image.asset(
          'assets/tmdb-approved.png',
          width: 96,
          height: 96,
          alignment: Alignment.centerLeft,
          semanticLabel: 'TMDB',
        ),
        const Text('Credits', style: TextStyle(fontSize: 20)),
        const Text(
          'This product uses the TMDB API but is not endorsed or certified by TMDB.',
        ),
        const SelectableText('https://www.themoviedb.org'),
        const Text(
          'Logo: Travis Bell / TMDB, CC BY-SA 4.0, unmodified; https://creativecommons.org/licenses/by-sa/4.0/ . Free noncommercial use subject to TMDB terms. Commercial use needs licensing review. Movie artwork belongs to its respective owners.',
        ),
      ],
    ),
  );
}
