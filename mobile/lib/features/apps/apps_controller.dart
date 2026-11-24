import 'dart:math';

import 'package:flutter/foundation.dart';

import '../../core/api/apps_api.dart';
import '../../core/api/models.dart';

class AppsController extends ChangeNotifier {
  AppsController(this.api, this.credential)
    : _prefix = Random.secure().nextInt(1 << 32).toRadixString(16);
  final AppsApi api;
  final PairCredential credential;
  final String _prefix;
  final icons = <String, Uint8List>{};
  List<TvApp> apps = [];
  int revision = 0, _generation = 0, _sequence = 0;
  bool needsRefresh = true;
  bool loading = false, _disposed = false, active = true;
  String? pending, message;
  AppFailure? error;
  void _emit() {
    if (!_disposed) notifyListeners();
  }

  bool _current(int generation) =>
      !_disposed && active && generation == _generation;
  Future<void> refresh() async {
    if (!active || loading || pending != null || _disposed) return;
    final generation = ++_generation;
    needsRefresh = true;
    api.cancel();
    loading = true;
    error = null;
    message = null;
    icons.clear();
    apps = [];
    _emit();
    try {
      final result = <TvApp>[];
      final seen = <String>{}, cursors = <String>{};
      String? cursor;
      int? version;
      do {
        final page = await api.page(credential, cursor);
        if (!_current(generation)) return;
        version ??= page.revision;
        if (version != page.revision ||
            result.length + page.apps.length > 64 ||
            page.apps.any((app) => !seen.add(app.package)) ||
            page.next != null &&
                (!cursors.add(page.next!) ||
                    page.apps.isEmpty ||
                    cursors.length > 2)) {
          throw const AppFailure(
            'protocol',
            'The app catalog changed. Refresh to try again.',
          );
        }
        result.addAll(page.apps);
        cursor = page.next;
      } while (cursor != null);
      apps = List.unmodifiable(result);
      revision = version;
      loading = false;
      needsRefresh = false;
      _emit();
      for (final app in apps) {
        if (!_current(generation)) return;
        try {
          final bytes = await api.icon(credential, app.package);
          if (!_current(generation)) return;
          if (bytes.length <= 8192) icons[app.package] = bytes;
          _emit();
        } on AppFailure catch (failure) {
          if (failure.code == 'unauthorized') rethrow;
          // Missing artwork never prevents opening the installed app.
        }
      }
    } catch (failure) {
      if (_current(generation)) {
        error = networkFailure(failure);
        loading = false;
        _emit();
      }
    }
  }

  Future<void> launch(TvApp app) async {
    if (!active ||
        loading ||
        pending != null ||
        _disposed ||
        needsRefresh ||
        error?.code == 'unauthorized' ||
        !apps.contains(app)) {
      return;
    }
    final generation = _generation;
    pending = app.package;
    error = null;
    message = 'Opening ${app.label}…';
    _emit();
    try {
      await api.launch(
        credential,
        app.package,
        revision,
        '$_prefix-${++_sequence}',
      );
      if (_current(generation)) {
        message =
            'Launch accepted. Use the physical remote inside ${app.label}; return to Family TV for phone control.';
      }
    } catch (failure) {
      if (_current(generation)) {
        error = networkFailure(failure);
        message = 'No automatic retry. Check the TV before trying again.';
      }
    } finally {
      if (_current(generation)) {
        pending = null;
        _emit();
      }
    }
  }

  void suspend() {
    active = false;
    needsRefresh = true;
    message = 'Connection paused. Refresh apps after returning to home Wi-Fi.';
    _generation++;
    api.cancel();
    loading = false;
    pending = null;
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
    api.cancel();
    super.dispose();
  }
}
