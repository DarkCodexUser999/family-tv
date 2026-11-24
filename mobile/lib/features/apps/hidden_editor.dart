import 'dart:async';
import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:flutter/foundation.dart';

import '../../core/api/apps_api.dart';
import '../../core/api/models.dart';

class HiddenDocument {
  HiddenDocument(this.revision, Iterable<String> values)
    : packages = Set.unmodifiable(values) {
    if (revision < 0 ||
        revision >= 999999999999999 ||
        packages.length > 256 ||
        packages.length != values.length ||
        packages.any(
          (p) =>
              p.length > 160 ||
              !RegExp(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')
                  .hasMatch(p),
        )) {
      throw const AppFailure('input', 'Invalid hidden configuration.');
    }
  }
  factory HiddenDocument.parse(Map<String, dynamic> j) {
    if (j['apiVersion'] != 1) {
      throw const AppFailure('protocol', 'Unsupported version.');
    }
    return HiddenDocument(
      j['revision'] as int,
      (j['packages'] as List).cast<String>(),
    );
  }
  final int revision;
  final Set<String> packages;
  Map<String, Object> toJson() => {
    'apiVersion': 1,
    'revision': revision,
    'packages': packages.toList(),
  };
}

class HiddenCatalog {
  HiddenCatalog(this.document, this.apps);
  final HiddenDocument document;
  final List<TvApp> apps;
}

abstract interface class HiddenApi {
  Future<HiddenCatalog> load(PairCredential c);
  Future<HiddenDocument> save(PairCredential c, HiddenDocument draft);
  void cancel();
}

class PinnedHiddenApi implements HiddenApi {
  final _http = PinnedAppsApi();
  Future<Map<String, dynamic>> _json(
    PairCredential c,
    String path, {
    String? cursor,
    HiddenDocument? draft,
  }) async {
    try {
      return jsonDecode(
        utf8.decode(
          await _http.request(
            c,
            path,
            cursor: cursor,
            method: draft == null ? 'GET' : 'PUT',
            ifMatch: draft == null ? null : '"${draft.revision}"',
            body: draft?.toJson(),
          ),
        ),
      ) as Map<String, dynamic>;
    } catch (e) {
      if (e is AppFailure) rethrow;
      throw const AppFailure('protocol', 'Invalid hidden-app response.');
    }
  }

  @override
  Future<HiddenCatalog> load(PairCredential c) async {
    final doc = HiddenDocument.parse(await _json(c, '/v1/hidden'));
    final apps = <TvApp>[], seen = <String>{}, cursors = <String>{};
    String? cursor;
    int? revision;
    do {
      final j = await _json(c, '/v1/hidden/apps', cursor: cursor);
      final normalized = {
        ...j,
        'apps': (j['apps'] as List)
            .map((a) => {...a as Map<String, dynamic>, 'hidden': false})
            .toList(),
      };
      final page = AppsPage.fromJson(normalized);
      revision ??= page.revision;
      if (revision != page.revision ||
          apps.length + page.apps.length > 64 ||
          page.apps.any((a) => !seen.add(a.package)) ||
          page.next != null &&
              (!cursors.add(page.next!) || cursors.length > 2)) {
        throw const AppFailure(
          'conflict',
          'App list changed. Reload before editing.',
        );
      }
      apps.addAll(page.apps);
      cursor = page.next;
    } while (cursor != null);
    if (HiddenDocument.parse(await _json(c, '/v1/hidden')).revision !=
        doc.revision) {
      throw const AppFailure(
        'conflict',
        'Hidden apps changed. Reload before editing.',
      );
    }
    return HiddenCatalog(doc, apps);
  }

  @override
  Future<HiddenDocument> save(PairCredential c, HiddenDocument d) async =>
      HiddenDocument.parse(await _json(c, '/v1/hidden', draft: d));
  @override
  void cancel() => _http.cancel();
}

class HiddenController extends ChangeNotifier {
  HiddenController(this.api, this.credential);
  final HiddenApi api;
  final PairCredential credential;
  HiddenCatalog? catalog;
  Set<String> draft = {};
  bool loading = false,
      saving = false,
      needsReload = false,
      active = true,
      _disposed = false;
  int _generation = 0;
  AppFailure? error;
  String? message;
  bool get dirty =>
      catalog != null && !setEquals(draft, catalog!.document.packages);
  bool get canSave => dirty && active && !loading && !saving && !needsReload;
  String label(String pkg) {
    for (final app in catalog?.apps ?? <TvApp>[]) {
      if (app.package == pkg) return app.label;
    }
    return pkg == 'com.onemainstream.sonyliv.android' ? 'Sony LIV' : pkg;
  }

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  bool _current(int n) => !_disposed && active && n == _generation;
  Future<void> load({bool discard = false}) async {
    if (!active || _disposed || loading || saving || dirty && !discard) return;
    final n = ++_generation;
    api.cancel();
    loading = true;
    error = null;
    _emit();
    try {
      final v = await api.load(credential);
      if (_current(n)) {
        catalog = v;
        draft = v.document.packages.toSet();
        needsReload = false;
        message = null;
      }
    } catch (e) {
      if (_current(n)) {
        error = networkFailure(e);
        needsReload = true;
      }
    } finally {
      if (_current(n)) {
        loading = false;
        _emit();
      }
    }
  }

  void toggle(String p, bool hidden) {
    if (!active || catalog == null || saving || loading) return;
    if (!catalog!.document.packages.contains(p) &&
        !catalog!.apps.any((a) => a.package == p)) {
      return;
    }
    if (hidden) {
      draft.add(p);
    } else {
      draft.remove(p);
    }
    _emit();
  }

  Future<void> save() async {
    if (!canSave) return;
    final n = _generation,
        d = HiddenDocument(catalog!.document.revision, draft);
    saving = true;
    error = null;
    _emit();
    try {
      final v = await api.save(credential, d);
      if (!_current(n)) return;
      if (v.revision != d.revision + 1 || !setEquals(v.packages, d.packages)) {
        throw const AppFailure(
          'protocol',
          'Save uncertain. Reload before retrying.',
        );
      }
      catalog = HiddenCatalog(v, catalog!.apps);
      draft = v.packages.toSet();
      message = 'Saved. Family TV updates when safe or on your next return.';
    } catch (e) {
      if (_current(n)) {
        error = networkFailure(e);
        needsReload = true;
      }
    } finally {
      if (_current(n)) {
        saving = false;
        _emit();
      }
    }
  }

  void suspend() {
    active = false;
    _generation++;
    api.cancel();
    loading = false;
    saving = false;
    needsReload = true;
    message = 'Connection paused. Reload to check the TV before saving again.';
    _emit();
  }

  void resume() {
    if (!_disposed) {
      active = true;
      _emit();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    api.cancel();
    super.dispose();
  }
}

class HiddenEditor extends StatefulWidget {
  const HiddenEditor({super.key, required this.controller});
  final HiddenController controller;
  @override
  State<HiddenEditor> createState() => _HiddenEditorState();
}

class _HiddenEditorState extends State<HiddenEditor>
    with WidgetsBindingObserver {
  HiddenController get c => widget.controller;
  String query = '';
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(c.load());
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState s) {
    if (s == AppLifecycleState.resumed) {
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

  Future<bool> confirm(String title, String detail, String action) async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: SingleChildScrollView(child: Text(detail)),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(action),
            ),
          ],
        ),
      ) ??
      false;
  Future<void> save() async {
    final old = c.catalog!.document.packages,
        changes = [
          for (final p in c.draft.difference(c.catalog!.document.packages))
            'Hide ${c.label(p)}',
          for (final p in c.catalog!.document.packages.difference(c.draft))
            'Show ${c.label(p)}',
        ];
    if (old.isNotEmpty || changes.isNotEmpty) {
      if (await confirm(
        'Apply visibility changes?',
        '${changes.join('\n')}\n\nOnly Family TV tiles change. Apps and logins stay untouched.',
        'Apply',
      )) {
        if (mounted) await c.save();
      }
    }
  }

  Future<void> reload() async {
    if (!c.dirty ||
        await confirm(
          'Discard unsaved changes?',
          'Reload replaces this draft with the TV’s saved visibility.',
          'Reload',
        )) {
      if (mounted) await c.load(discard: true);
    }
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) {
      final packages = <String>{
        ...?c.catalog?.document.packages,
        ...?c.catalog?.apps.map((a) => a.package),
      }.toList()..sort((a, b) => c.label(a).compareTo(c.label(b)));
      return PopScope(
        canPop: !c.dirty && !c.saving,
        onPopInvokedWithResult: (didPop, result) async {
          if (!didPop &&
              !c.saving &&
              await confirm(
                'Discard unsaved changes?',
                'These changes are not saved to the TV.',
                'Discard',
              )) {
            if (context.mounted) Navigator.pop(context);
          }
        },
        child: Scaffold(
          appBar: AppBar(
            title: const Text('Hidden apps'),
            actions: [
              TextButton(
                onPressed: c.canSave ? save : null,
                child: const Text('Save'),
              ),
            ],
          ),
          body: SafeArea(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const Text(
                  'Switch ON to hide an app from Family TV. Switch OFF to show it again. Nothing is uninstalled and logins stay unchanged.',
                ),
                TextField(
                  decoration: const InputDecoration(labelText: 'Search apps'),
                  onChanged: (v) => setState(() => query = v),
                ),
                if (c.loading || c.saving) const LinearProgressIndicator(),
                if (c.error != null || c.message != null)
                  Semantics(
                    liveRegion: true,
                    child: Text(c.error?.message ?? c.message!),
                  ),
                OutlinedButton(
                  onPressed: c.loading || c.saving || !c.active ? null : reload,
                  child: const Text('Reload hidden apps'),
                ),
                for (final p in packages)
                  if (c.label(p).toLowerCase().contains(query.toLowerCase()))
                    SwitchListTile(
                      title: Text(c.label(p)),
                      subtitle: Text(
                        c.draft.contains(p) ? 'Hidden' : 'Visible',
                      ),
                      value: c.draft.contains(p),
                      onChanged:
                          c.loading ||
                              c.saving ||
                              !c.active ||
                              !(c.catalog?.apps.any(
                                    (app) => app.package == p,
                                  ) ??
                                  false)
                          ? null
                          : (v) => c.toggle(p, v),
                    ),
              ],
            ),
          ),
        ),
      );
    },
  );
}
