import 'package:flutter/foundation.dart';

import '../../core/api/models.dart';
import 'status_api.dart';

class StatusController extends ChangeNotifier {
  StatusController(this.api, this.credential);
  final StatusApi api;
  final PairCredential credential;
  TvStatus? value;
  AppFailure? error;
  bool busy = false, active = true, _disposed = false;
  int _generation = 0;
  Future<void> refresh() async {
    if (busy || !active || _disposed) return;
    final stamp = ++_generation;
    busy = true;
    error = null;
    notifyListeners();
    try {
      final result = await api.fetch(credential);
      if (stamp == _generation && !_disposed && active) value = result;
    } catch (e) {
      if (stamp == _generation && !_disposed && active) {
        value = null;
        error = e is AppFailure
            ? e
            : const AppFailure(
                'connection',
                'Status unavailable. Retry when connected.',
              );
      }
    } finally {
      if (stamp == _generation && !_disposed) {
        busy = false;
        notifyListeners();
      }
    }
  }

  void suspend() {
    if (_disposed) return;
    active = false;
    _generation++;
    api.cancel();
    busy = false;
    value = null;
    notifyListeners();
  }

  void resume() {
    if (_disposed) return;
    active = true;
    refresh();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    api.cancel();
    super.dispose();
  }
}
