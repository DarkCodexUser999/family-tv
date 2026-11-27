import 'dart:async';

import 'package:material_ui/material_ui.dart';

import '../../core/api/models.dart';
import '../../core/api/apps_api.dart';
import '../apps/apps_controller.dart';
import '../apps/apps_screen.dart';
import 'remote_controller.dart';
import '../status/status_api.dart';
import '../status/status_controller.dart';
import '../status/status_screen.dart';

class RemoteScreen extends StatefulWidget {
  const RemoteScreen({
    super.key,
    required this.credential,
    required this.controller,
    this.appsFactory,
  });
  final PairCredential credential;
  final RemoteController controller;
  final AppsApi Function()? appsFactory;
  @override
  State<RemoteScreen> createState() => _RemoteScreenState();
}

class _RemoteScreenState extends State<RemoteScreen>
    with WidgetsBindingObserver {
  RemoteController get c => widget.controller;
  bool _appsOpen = false;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.connect(widget.credential));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_appsOpen) {
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

  Widget key(String action, IconData icon, double size) => SizedBox(
    width: size,
    height: size,
    child: IconButton.filledTonal(
      tooltip: action == 'OK'
          ? 'OK'
          : action[0] + action.substring(1).toLowerCase(),
      onPressed: c.canSend ? () => c.send(action) : null,
      icon: Icon(icon, size: 36),
    ),
  );
  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) => Scaffold(
      appBar: AppBar(
        title: const Text('Family TV remote'),
        actions: [
          IconButton(
            tooltip: 'Connection status',
            icon: const Icon(Icons.info_outline),
            onPressed: () async {
              _appsOpen = true;
              c.suspend();
              await Navigator.push(
                context,
                MaterialPageRoute<void>(
                  builder: (_) => StatusScreen(
                    controller: StatusController(
                      StatusApi(),
                      widget.credential,
                    ),
                  ),
                ),
              );
              if (mounted) {
                _appsOpen = false;
                if (WidgetsBinding.instance.lifecycleState ==
                    AppLifecycleState.resumed) {
                  c.resume();
                }
              }
            },
          ),
          IconButton(
            tooltip: 'TV apps',
            icon: const Icon(Icons.apps),
            onPressed: () async {
              _appsOpen = true;
              c.suspend();
              await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (_) => AppsScreen(
                    controller: AppsController(
                      widget.appsFactory?.call() ?? PinnedAppsApi(),
                      widget.credential,
                    ),
                  ),
                ),
              );
              if (mounted) {
                _appsOpen = false;
                if (WidgetsBinding.instance.lifecycleState ==
                    AppLifecycleState.resumed) {
                  c.resume();
                }
              }
            },
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: ListView(
              padding: const EdgeInsets.all(24),
              children: [
                Semantics(
                  liveRegion: true,
                  child: Text(
                    c.connecting
                        ? 'Connecting…'
                        : c.connected
                        ? 'Connected to Family TV'
                        : 'Disconnected',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                ),
                const SizedBox(height: 12),
                Text(
                  !c.connected
                      ? 'Reconnect to enable the pad. Check home Wi-Fi and Remote access on the TV.'
                      : c.pending != null
                      ? 'Waiting for the TV to confirm your last input. Controls unlock after confirmation.'
                      : c.state?.canNavigate == true
                      ? 'Navigate Family TV using the pad below.'
                      : c.state?.foreground == true
                      ? 'Family TV is opening an app. Please wait.'
                      : 'Family TV must be open on the TV. Use the physical remote to return from another app.',
                ),
                if (c.error != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    child: Semantics(
                      liveRegion: true,
                      child: Text(
                        c.error!.message,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                  ),
                if (c.pending != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 16),
                    child: Text(
                      'Sent ${c.lastAction}…',
                      textAlign: TextAlign.center,
                    ),
                  ),
                const SizedBox(height: 24),
                LayoutBuilder(
                  builder: (context, constraints) {
                    final size = (constraints.maxWidth / 3 - 10).clamp(
                      60.0,
                      88.0,
                    );
                    return Column(
                      children: [
                        key('UP', Icons.keyboard_arrow_up, size),
                        const SizedBox(height: 10),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            key('LEFT', Icons.keyboard_arrow_left, size),
                            const SizedBox(width: 10),
                            SizedBox(
                              width: size,
                              height: size,
                              child: FilledButton(
                                onPressed: c.canSend
                                    ? () => c.send('OK')
                                    : null,
                                child: const Text('OK'),
                              ),
                            ),
                            const SizedBox(width: 10),
                            key('RIGHT', Icons.keyboard_arrow_right, size),
                          ],
                        ),
                        const SizedBox(height: 10),
                        key('DOWN', Icons.keyboard_arrow_down, size),
                      ],
                    );
                  },
                ),
                const SizedBox(height: 24),
                Wrap(
                  alignment: WrapAlignment.center,
                  spacing: 12,
                  runSpacing: 12,
                  children: [
                    for (final item in const [
                      ('HOME', Icons.home_outlined),
                      ('BACK', Icons.arrow_back),
                      ('OPTIONS', Icons.more_horiz),
                    ])
                      SizedBox(
                        width: 96,
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                          ),
                          onPressed: c.canSend ? () => c.send(item.$1) : null,
                          child: Padding(
                            padding: const EdgeInsets.symmetric(vertical: 12),
                            child: Column(
                              children: [
                                Icon(item.$2),
                                Text(
                                  item.$1[0] +
                                      item.$1.substring(1).toLowerCase(),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 24),
                if (!c.connected)
                  OutlinedButton(
                    onPressed: c.connecting
                        ? null
                        : () => c.connect(widget.credential),
                    child: const Text('Reconnect'),
                  ),
                const Text(
                  'Home and Back apply to Family TV only. This app cannot control Netflix, YouTube, TV power or volume.',
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ),
    ),
  );
}
