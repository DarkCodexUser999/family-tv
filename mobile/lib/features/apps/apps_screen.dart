import 'dart:async';

import 'package:material_ui/material_ui.dart';

import 'apps_controller.dart';
import 'hidden_editor.dart';
import '../artwork/artwork_api.dart';
import '../artwork/artwork_screen.dart';
import '../../core/api/rows_api.dart';
import '../rows/rows_controller.dart';
import '../rows/rows_screen.dart';

class AppsScreen extends StatefulWidget {
  const AppsScreen({super.key, required this.controller, this.rowsFactory});
  final AppsController controller;
  final RowsApi Function()? rowsFactory;
  @override
  State<AppsScreen> createState() => _AppsScreenState();
}

class _AppsScreenState extends State<AppsScreen> with WidgetsBindingObserver {
  String query = '';
  bool _rowsOpen = false;
  AppsController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.refresh());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_rowsOpen) {
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
              tooltip: 'TV artwork',
              icon: const Icon(Icons.image_outlined),
              onPressed:
                  c.loading ||
                      c.pending != null ||
                      c.needsRefresh ||
                      c.apps.isEmpty
                  ? null
                  : () async {
                      _rowsOpen = true;
                      c.suspend();
                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => ArtworkScreen(
                            apps: c.apps,
                            api: ArtworkApi(c.credential),
                          ),
                        ),
                      );
                      if (mounted) {
                        _rowsOpen = false;
                        if (WidgetsBinding.instance.lifecycleState ==
                            AppLifecycleState.resumed) {
                          c.resume();
                          await c.refresh();
                        }
                      }
                    },
            ),
            IconButton(
              tooltip: 'Hidden apps',
              icon: const Icon(Icons.visibility_off_outlined),
              onPressed:
                  c.loading ||
                      c.pending != null ||
                      c.needsRefresh ||
                      c.error?.code == 'unauthorized'
                  ? null
                  : () async {
                      _rowsOpen = true;
                      c.suspend();
                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => HiddenEditor(
                            controller: HiddenController(
                              PinnedHiddenApi(),
                              c.credential,
                            ),
                          ),
                        ),
                      );
                      if (mounted) {
                        _rowsOpen = false;
                        if (WidgetsBinding.instance.lifecycleState ==
                            AppLifecycleState.resumed) {
                          c.resume();
                          await c.refresh();
                        }
                      }
                    },
            ),
            IconButton(
              tooltip: 'Edit TV rows',
              icon: const Icon(Icons.view_agenda_outlined),
              onPressed:
                  c.loading ||
                      c.pending != null ||
                      c.needsRefresh ||
                      c.error?.code == 'unauthorized'
                  ? null
                  : () async {
                      _rowsOpen = true;
                      c.suspend();
                      await Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (_) => RowsScreen(
                            controller: RowsController(
                              widget.rowsFactory?.call() ?? PinnedRowsApi(),
                              c.credential,
                              c.apps,
                            ),
                          ),
                        ),
                      );
                      if (mounted) {
                        _rowsOpen = false;
                        if (WidgetsBinding.instance.lifecycleState ==
                            AppLifecycleState.resumed) {
                          c.resume();
                          await c.refresh();
                        }
                      }
                    },
            ),
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
