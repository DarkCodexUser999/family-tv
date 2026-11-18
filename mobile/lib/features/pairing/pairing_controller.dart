import 'package:flutter/foundation.dart';

import '../../core/api/models.dart';
import '../../core/api/tv_api.dart';
import '../../core/storage/credential_store.dart';
import '../../core/discovery/tv_discovery.dart';

enum PairPhase { choose, pin, paired, saved }

class PairingController extends ChangeNotifier {
  PairingController({
    required this.api,
    required this.store,
    required this.discovery,
  });
  final TvApi api;
  final CredentialStore store;
  final TvDiscovery discovery;
  PairPhase phase = PairPhase.choose;
  bool busy = false, scanning = false, _disposed = false;
  int _epoch = 0;
  AppFailure? error;
  List<TvCandidate> candidates = [];
  PairCredential? credential;
  TvEndpoint? endpoint;
  String? certificate;
  TvInfo? info;
  void _emit() {
    if (!_disposed) notifyListeners();
  }

  Future<void> initialise() async {
    busy = true;
    _emit();
    try {
      credential = await store.load();
      if (credential != null) phase = PairPhase.saved;
    } on AppFailure catch (failure) {
      error = failure;
    } finally {
      busy = false;
      _emit();
    }
  }

  Future<void> discover() async {
    if (scanning || busy) return;
    final epoch = ++_epoch;
    error = null;
    scanning = true;
    candidates = [];
    _emit();
    try {
      await discovery.scan((values) {
        if (epoch == _epoch) {
          candidates = values;
          _emit();
        }
      });
    } on AppFailure catch (failure) {
      if (epoch == _epoch) error = failure;
    } finally {
      if (epoch == _epoch) {
        scanning = false;
        _emit();
      }
    }
  }

  Future<void> checkTv(String address, String hash) async {
    if (busy) return;
    busy = true;
    error = null;
    _emit();
    try {
      await pauseDiscovery();
      final target = TvEndpoint.parse(address), pin = fingerprint(hash);
      final value = await api.info(target, pin);
      endpoint = target;
      certificate = pin;
      info = value;
      if (!value.pairingOpen) {
        throw const AppFailure(
          'window',
          'On TV, open Family TV settings → Pair a phone, then check the TV again.',
        );
      }
      phase = PairPhase.pin;
    } catch (failure) {
      error = networkFailure(failure);
    } finally {
      busy = false;
      _emit();
    }
  }

  Future<void> pair(String pin, String name) async {
    if (busy || endpoint == null || info == null || certificate == null) return;
    busy = true;
    error = null;
    _emit();
    try {
      final value = await api.pair(endpoint!, certificate!, info!, pin, name);
      await store.save(value);
      credential = value;
      phase = PairPhase.paired;
      info = null;
    } catch (failure) {
      error = networkFailure(failure);
    } finally {
      busy = false;
      _emit();
    }
  }

  Future<void> forget() async {
    if (busy) return;
    busy = true;
    error = null;
    _emit();
    try {
      await store.forget();
      credential = null;
      phase = PairPhase.choose;
      info = null;
    } on AppFailure catch (failure) {
      error = failure;
    } finally {
      busy = false;
      _emit();
    }
  }

  Future<void> pauseDiscovery() async {
    _epoch++;
    scanning = false;
    await discovery.stop();
    _emit();
  }

  void changeTv() {
    if (!busy) {
      phase = PairPhase.choose;
      error = null;
      info = null;
      _emit();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _epoch++;
    discovery.stop();
    super.dispose();
  }
}
