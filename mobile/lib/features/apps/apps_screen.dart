import 'dart:async';

import 'package:material_ui/material_ui.dart';

import 'apps_controller.dart';

class AppsScreen extends StatefulWidget {
  const AppsScreen({super.key, required this.controller});
  final AppsController controller;
  @override
  State<AppsScreen> createState() => _AppsScreenState();
}

class _AppsScreenState extends State<AppsScreen> with WidgetsBindingObserver {
  String query = '';
  AppsController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.refresh());
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

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) {
      final apps = c.apps
          .where((app) => app.label.toLowerCase().contains(query.toLowerCase()))
          .toList();
      return Scaffold(
        appBar: AppBar(
          title: const Text('TV apps'),
          actions: [
            IconButton(
              tooltip: 'Refresh apps',
              onPressed: c.loading || c.pending != null ? null : c.refresh,
              icon: const Icon(Icons.refresh),
            ),
          ],
        ),
        body: SafeArea(
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: TextField(
                  decoration: const InputDecoration(
                    labelText: 'Search installed apps',
                    prefixIcon: Icon(Icons.search),
                  ),
                  onChanged: (value) => setState(() => query = value),
                ),
              ),
              if (c.loading) const LinearProgressIndicator(),
              if (c.error != null || c.message != null)
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  child: Semantics(
                    liveRegion: true,
                    child: Text(c.error?.message ?? c.message!),
                  ),
                ),
              if (!c.loading && apps.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Text(
                    'No matching apps. Refresh after installing apps on the TV.',
                  ),
                ),
              Expanded(
                child: GridView.builder(
                  padding: const EdgeInsets.all(16),
                  gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                    maxCrossAxisExtent: 300,
                    mainAxisExtent:
                        MediaQuery.textScalerOf(context).scale(160) + 100,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                  ),
                  itemCount: apps.length,
                  itemBuilder: (context, index) {
                    final app = apps[index], bytes = c.icons[app.package];
                    return Card(
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Column(
                          children: [
                            SizedBox(
                              height: 72,
                              child: bytes == null
                                  ? const Icon(Icons.tv, size: 48)
                                  : Image.memory(
                                      bytes,
                                      cacheWidth: 192,
                                      errorBuilder: (_, error, stack) =>
                                          const Icon(Icons.tv, size: 48),
                                    ),
                            ),
                            const SizedBox(height: 8),
                            Expanded(
                              child: Text(
                                app.label,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                textAlign: TextAlign.center,
                              ),
                            ),
                            FilledButton(
                              onPressed:
                                  c.loading ||
                                      c.pending != null ||
                                      !c.active ||
                                      c.needsRefresh ||
                                      c.error?.code == 'unauthorized'
                                  ? null
                                  : () => c.launch(app),
                              child: Text(
                                c.pending == app.package ? 'Opening…' : 'Open',
                              ),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}
