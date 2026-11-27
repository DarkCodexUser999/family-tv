import 'package:material_ui/material_ui.dart';

import 'status_controller.dart';

class StatusScreen extends StatefulWidget {
  const StatusScreen({super.key, required this.controller});
  final StatusController controller;
  @override
  State<StatusScreen> createState() => _StatusScreenState();
}

class _StatusScreenState extends State<StatusScreen>
    with WidgetsBindingObserver {
  StatusController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    c.refresh();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      c.resume();
    } else {
      c.suspend();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    c.dispose();
    super.dispose();
  }

  static const jobLabels = {
    'idle': 'No refresh requested',
    'phone_required': 'Waiting for a phone with TMDB setup',
    'running': 'A phone is refreshing artwork',
    'complete': 'Artwork refresh completed',
    'canceled': 'Refresh canceled',
    'error': 'Refresh failed — retry from Movie backdrop',
  };
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) => Scaffold(
      appBar: AppBar(title: const Text('Connection and status')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Semantics(
              liveRegion: true,
              child: Text(
                c.busy
                    ? 'Checking TV…'
                    : c.value != null
                    ? 'Connected to Family TV'
                    : 'Status unavailable',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ),
            const SizedBox(height: 16),
            if (c.error != null) Text(c.error!.message),
            if (c.value case final value?) ...[
              Text(
                value.canNavigate
                    ? 'Controls ready'
                    : value.foreground
                    ? 'Controls paused while Family TV is busy'
                    : 'Open Family TV on the TV to use the pad',
              ),
              const SizedBox(height: 16),
              Text('Artwork: ${jobLabels[value.job]}'),
              Text('App catalog version: ${value.revision}'),
              const SizedBox(height: 16),
              const Text('Launcher capabilities'),
              for (final feature in value.capabilities.entries)
                Text(
                  '${feature.key}: ${feature.value ? "available" : "unavailable"}',
                ),
            ],
            const SizedBox(height: 24),
            FilledButton(
              onPressed: c.busy || !c.active ? null : c.refresh,
              child: const Text('Refresh status'),
            ),
            const Text(
              'Snapshot only; refresh to check again. Controls apply to Family TV, not other apps, TV power or volume.',
            ),
          ],
        ),
      ),
    ),
  );
}
