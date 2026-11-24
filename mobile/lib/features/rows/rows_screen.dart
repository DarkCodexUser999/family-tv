import 'dart:async';

import 'package:material_ui/material_ui.dart';

import 'rows_controller.dart';

class RowsScreen extends StatefulWidget {
  const RowsScreen({super.key, required this.controller});
  final RowsController controller;
  @override
  State<RowsScreen> createState() => _RowsScreenState();
}

class _RowsScreenState extends State<RowsScreen> with WidgetsBindingObserver {
  RowsController get c => widget.controller;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.load());
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

  Future<bool> _discard(String action) async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Discard unsaved changes?'),
          content: const Text(
            'Your draft stays on this phone until Save. Discarding it keeps the current TV rows.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Keep editing'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(action),
            ),
          ],
        ),
      ) ??
      false;
  Future<void> _reload() async {
    if (!c.dirty || await _discard('Reload from TV')) {
      if (mounted) await c.load(discard: true);
    }
  }

  Future<void> _add(int row) async {
    final available = c.unassigned;
    final pkg = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Add to row ${row + 1}'),
        content: SizedBox(
          width: 360,
          height: 320,
          child: available.isEmpty
              ? const Text('All installed apps are assigned.')
              : ListView(
                  children: [
                    for (final app in available)
                      ListTile(
                        title: Text(app.label),
                        onTap: () => Navigator.pop(context, app.package),
                      ),
                  ],
                ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
    if (pkg != null && mounted) c.add(row, pkg);
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) => PopScope(
      canPop: !c.dirty && !c.saving,
      onPopInvokedWithResult: (didPop, result) async {
        if (!didPop && !c.saving && await _discard('Discard')) {
          if (context.mounted) Navigator.pop(context);
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: const Text('TV rows'),
          actions: [
            TextButton(
              onPressed: c.canSave ? c.save : null,
              child: const Text('Save'),
            ),
          ],
        ),
        body: SafeArea(
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const Text(
                'Reorder apps or move them between rows. Unassigned apps stay visible in the TV’s last row. Changes reach the TV only after Save.',
              ),
              if (c.loading || c.saving) const LinearProgressIndicator(),
              if (c.error != null || c.message != null)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Semantics(
                    liveRegion: true,
                    child: Text(c.error?.message ?? c.message!),
                  ),
                ),
              OutlinedButton.icon(
                onPressed: c.loading || c.saving || !c.active ? null : _reload,
                icon: const Icon(Icons.refresh),
                label: const Text('Reload TV rows'),
              ),
              for (int row = 0; row < c.rows.length; row++)
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Row ${row + 1}',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        Wrap(
                          children: [
                            IconButton(
                              tooltip: 'Move row ${row + 1} up',
                              onPressed: row > 0 && !c.saving && !c.loading
                                  ? () => c.reorderRow(row, -1)
                                  : null,
                              icon: const Icon(Icons.arrow_upward),
                            ),
                            IconButton(
                              tooltip: 'Move row ${row + 1} down',
                              onPressed:
                                  row < c.rows.length - 1 &&
                                      !c.saving &&
                                      !c.loading
                                  ? () => c.reorderRow(row, 1)
                                  : null,
                              icon: const Icon(Icons.arrow_downward),
                            ),
                            IconButton(
                              tooltip: 'Remove row ${row + 1}',
                              onPressed:
                                  c.rows.length > 1 && !c.saving && !c.loading
                                  ? () => c.removeRow(row)
                                  : null,
                              icon: const Icon(Icons.delete_outline),
                            ),
                          ],
                        ),
                        for (int index = 0; index < c.rows[row].length; index++)
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(c.label(c.rows[row][index])),
                                Wrap(
                                  children: [
                                    IconButton(
                                      tooltip:
                                          'Move ${c.label(c.rows[row][index])} earlier',
                                      onPressed:
                                          index > 0 && !c.saving && !c.loading
                                          ? () => c.reorder(row, index, -1)
                                          : null,
                                      icon: const Icon(Icons.arrow_back),
                                    ),
                                    IconButton(
                                      tooltip:
                                          'Move ${c.label(c.rows[row][index])} later',
                                      onPressed:
                                          index < c.rows[row].length - 1 &&
                                              !c.saving &&
                                              !c.loading
                                          ? () => c.reorder(row, index, 1)
                                          : null,
                                      icon: const Icon(Icons.arrow_forward),
                                    ),
                                    PopupMenuButton<int>(
                                      tooltip: 'Move app to row',
                                      enabled: !c.saving && !c.loading,
                                      onSelected: (target) =>
                                          c.move(row, index, target),
                                      itemBuilder: (context) => [
                                        for (
                                          int target = 0;
                                          target < c.rows.length;
                                          target++
                                        )
                                          if (target != row)
                                            PopupMenuItem(
                                              value: target,
                                              enabled:
                                                  c.rows[target].length < 6,
                                              child: Text(
                                                'Row ${target + 1}${c.rows[target].length >= 6 ? ' (full)' : ''}',
                                              ),
                                            ),
                                      ],
                                      icon: const Icon(
                                        Icons.drive_file_move_outline,
                                      ),
                                    ),
                                    IconButton(
                                      tooltip:
                                          'Unassign ${c.label(c.rows[row][index])}',
                                      onPressed: !c.saving && !c.loading
                                          ? () => c.unassign(row, index)
                                          : null,
                                      icon: const Icon(
                                        Icons.remove_circle_outline,
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        OutlinedButton.icon(
                          onPressed:
                              c.rows[row].length < 6 && !c.saving && !c.loading
                              ? () => _add(row)
                              : null,
                          icon: const Icon(Icons.add),
                          label: const Text('Add app'),
                        ),
                      ],
                    ),
                  ),
                ),
              OutlinedButton.icon(
                onPressed:
                    c.rows.length < 4 &&
                        c.base != null &&
                        !c.saving &&
                        !c.loading
                    ? c.addRow
                    : null,
                icon: const Icon(Icons.add),
                label: const Text('Add row'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
