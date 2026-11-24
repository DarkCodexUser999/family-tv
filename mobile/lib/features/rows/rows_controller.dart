import 'package:flutter/foundation.dart';

import '../../core/api/apps_api.dart';
import '../../core/api/models.dart';
import '../../core/api/rows_api.dart';

class RowsController extends ChangeNotifier {
  RowsController(this.api, this.credential, this.apps);
  final RowsApi api;
  final PairCredential credential;
  final List<TvApp> apps;
  RowsDocument? base;
  List<List<String>> rows = [];
  bool loading = false,
      saving = false,
      dirty = false,
      needsReload = false,
      active = true,
      _disposed = false;
  int _generation = 0;
  String? message;
  AppFailure? error;
  bool get canSave =>
      active &&
      base != null &&
      dirty &&
      !loading &&
      !saving &&
      !needsReload &&
      error?.code != 'unauthorized';
  List<TvApp> get unassigned {
    final used = rows.expand((r) => r).toSet();
    return apps.where((app) => !used.contains(app.package)).toList();
  }

  String label(String pkg) {
    for (final app in apps) {
      if (app.package == pkg) return app.label;
    }
    return pkg;
  }

  void _emit() {
    if (!_disposed) notifyListeners();
  }

  bool _current(int generation) =>
      !_disposed && active && generation == _generation;
  void _accept(RowsDocument value) {
    base = value;
    rows = value.rows.map((r) => r.toList()).toList();
    dirty = false;
    needsReload = false;
  }

  Future<void> load({bool discard = false}) async {
    if (_disposed || !active || loading || saving || dirty && !discard) return;
    final generation = ++_generation;
    api.cancel();
    loading = true;
    error = null;
    message = null;
    _emit();
    try {
      final value = await api.load(credential);
      if (_current(generation)) _accept(value);
    } catch (failure) {
      if (_current(generation)) {
        error = networkFailure(failure);
        needsReload = true;
      }
    } finally {
      if (_current(generation)) {
        loading = false;
        _emit();
      }
    }
  }

  void _edit(void Function() change) {
    if (_disposed || !active || base == null || loading || saving) return;
    change();
    dirty =
        rows.length != base!.rows.length ||
        List.generate(
          rows.length,
          (i) => i,
        ).any((i) => !listEquals(rows[i], base!.rows[i]));
    message = null;
    _emit();
  }

  void reorder(int row, int index, int delta) => _edit(() {
    final target = index + delta;
    if (target >= 0 && target < rows[row].length) {
      final pkg = rows[row].removeAt(index);
      rows[row].insert(target, pkg);
    }
  });
  void reorderRow(int row, int delta) => _edit(() {
    final target = row + delta;
    if (target >= 0 && target < rows.length) {
      final value = rows.removeAt(row);
      rows.insert(target, value);
    }
  });
  void move(int row, int index, int target) => _edit(() {
    if (row != target &&
        target >= 0 &&
        target < rows.length &&
        rows[target].length < 6) {
      rows[target].add(rows[row].removeAt(index));
    }
  });
  void add(int row, String pkg) => _edit(() {
    if (rows[row].length < 6 &&
        apps.any((a) => a.package == pkg) &&
        !rows.any((r) => r.contains(pkg))) {
      rows[row].add(pkg);
    }
  });
  void unassign(int row, int index) => _edit(() => rows[row].removeAt(index));
  void addRow() => _edit(() {
    if (rows.length < 4) rows.add([]);
  });
  void removeRow(int row) => _edit(() {
    if (rows.length > 1) rows.removeAt(row);
  });
  Future<void> save() async {
    if (!canSave) return;
    final draft = RowsDocument(base!.revision, rows), generation = _generation;
    saving = true;
    error = null;
    message = 'Saving TV rows…';
    _emit();
    try {
      final result = await api.save(credential, draft);
      if (!_current(generation)) return;
      if (result.revision != draft.revision + 1 ||
          result.rows.length != draft.rows.length ||
          List.generate(
            result.rows.length,
            (i) => i,
          ).any((i) => !listEquals(result.rows[i], draft.rows[i]))) {
        throw const AppFailure(
          'protocol',
          'Save result is uncertain. Reload TV rows before retrying.',
        );
      }
      _accept(result);
      message = 'Rows saved. Family TV applies them when safe or on your next return.';
    } catch (failure) {
      if (_current(generation)) {
        error = networkFailure(failure);
        needsReload = true;
        message = 'Your draft is kept. Reload TV rows before another save.';
      }
    } finally {
      if (_current(generation)) {
        saving = false;
        _emit();
      }
    }
  }

  void suspend() {
    if (_disposed) return;
    active = false;
    _generation++;
    api.cancel();
    needsReload = true;
    message = saving
        ? 'Save result uncertain. Reload TV rows before retrying.'
        : 'Connection paused. Reload TV rows after returning.';
    loading = false;
    saving = false;
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
