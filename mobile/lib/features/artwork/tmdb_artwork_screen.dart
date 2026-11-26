import 'package:material_ui/material_ui.dart';

import '../../core/api/models.dart';
import '../../core/api/tmdb_client.dart';
import 'artwork_api.dart';
import 'tmdb_settings.dart';
import 'refresh_controller.dart';
import 'tmdb_setup_screen.dart';

class TmdbArtworkScreen extends StatefulWidget {
  const TmdbArtworkScreen({super.key, required this.pkg, required this.api});
  final String pkg;
  final ArtworkApi api;
  @override
  State<TmdbArtworkScreen> createState() => _TmdbArtworkScreenState();
}

class _TmdbArtworkScreenState extends State<TmdbArtworkScreen>
    with WidgetsBindingObserver {
  final query = TextEditingController(), tmdb = TmdbClient();
  final settings = const TmdbSettings();
  late final provider = ProviderApi(widget.api.credential);
  late final refresh = RefreshController(provider, widget.api, tmdb, settings);
  List<TmdbChoice> choices = [];
  bool busy = false;
  int generation = 0;
  String message =
      'Enable optional artwork, search a movie, then choose its backdrop.';
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  void cancel() {
    generation++;
    refresh.cancel();
    if (mounted) {
      setState(() {
        message = 'Stopped. Check the TV before retrying.';
      });
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) cancel();
  }

  @override
  void dispose() {
    generation++;
    refresh.cancel();
    query.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> action(Future<void> Function(void Function(String)) work) async {
    if (busy) return;
    final stamp = ++generation;
    setState(() => busy = true);
    void update(String value) {
      if (mounted && stamp == generation) setState(() => message = value);
    }

    try {
      await work(update);
    } catch (e) {
      update(
        e is AppFailure
            ? e.message
            : 'Artwork request failed. Previous artwork is retained.',
      );
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Movie backdrop')),
    body: ListView(
      padding: const EdgeInsets.all(16),
      children: [
        OutlinedButton(
          onPressed: busy
              ? null
              : () => Navigator.push(
                  context,
                  MaterialPageRoute<void>(
                    builder: (_) => const TmdbSetupScreen(),
                  ),
                ),
          child: const Text('TMDB setup and credits'),
        ),
        FilledButton(
          onPressed: busy
              ? null
              : () => action((update) async {
                  await provider.post('/v1/provider/enable');
                  update('Phone artwork enabled on TV.');
                }),
          child: const Text('Enable phone artwork on TV'),
        ),
        OutlinedButton(
          onPressed: busy
              ? null
              : () => action((update) async {
                  await provider.post('/v1/provider/disable');
                  update(
                    'Phone artwork disabled. Existing images are retained.',
                  );
                }),
          child: const Text('Disable phone artwork on TV'),
        ),
        TextField(
          controller: query,
          maxLength: 100,
          enabled: !busy,
          decoration: const InputDecoration(labelText: 'Movie title'),
        ),
        FilledButton(
          onPressed: busy
              ? null
              : () => action((update) async {
                  final stamp = generation;
                    final token = await settings.load();
                    if (!mounted || stamp != generation) return;
                  if (token == null) {
                    throw const AppFailure(
                      'phone_required',
                      'Set up your own TMDB token on this phone first.',
                    );
                  }
                  final found = await tmdb.search(token, query.text);
                  if (mounted && stamp == generation) setState(() => choices = found);
                  update(
                    found.isEmpty ? 'No matching backdrops.' : 'Choose a movie to replace this app’s uploaded backdrop.',
                  );
                }),
          child: const Text('Search TMDB'),
        ),
        Semantics(liveRegion: true, child: Text(message)),
        if (busy) const LinearProgressIndicator(),
        if (busy) TextButton(onPressed: cancel, child: const Text('Cancel')),
        for (final choice in choices)
          ListTile(
            title: Text(choice.title),
            enabled: !busy,
            trailing: const Icon(Icons.wallpaper),
            onTap: busy
                ? null
                : () => action(
                    (update) => refresh.run(widget.pkg, choice, state: update),
                  ),
          ),
      ],
    ),
  );
}
