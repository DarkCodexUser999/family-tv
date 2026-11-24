import 'dart:async';

import 'package:nsd/nsd.dart' as nsd;
import 'package:flutter/foundation.dart';

import '../api/models.dart';

class TvCandidate {
  const TvCandidate(this.name, this.endpoint);
  final String name;
  final TvEndpoint endpoint;
}

abstract interface class TvDiscovery {
  Future<void> scan(void Function(List<TvCandidate>) update);
  Future<void> stop();
}

class NsdTvDiscovery implements TvDiscovery {
  nsd.Discovery? _active;
  void Function()? _listener;
  int _generation = 0;
  @override
  Future<void> scan(void Function(List<TvCandidate>) update) async {
    await stop();
    final generation = ++_generation;
    final pending = nsd.startDiscovery(
      '_familytv._tcp',
      ipLookupType: nsd.IpLookupType.v4,
    );
    // A timeout or app exit must also clean up a native registration that completes late.
    unawaited(
      pending
          .then((d) async {
            if (generation != _generation) await nsd.stopDiscovery(d);
          })
          .catchError((Object _) {}),
    );
    try {
      final d = await pending.timeout(const Duration(seconds: 8));
      if (generation != _generation) return;
      _active = d;
      void emit() {
        final found = <String, TvCandidate>{};
        for (final service in d.services.take(32)) {
          for (final ip in service.addresses ?? []) {
            try {
              final endpoint = TvEndpoint.parse(
                '${ip.address}:${service.port}',
              );
              found[endpoint.toString()] = TvCandidate(
                service.name ?? 'Family TV',
                endpoint,
              );
            } on AppFailure {
              /* Ignore public/unsupported addresses. */
            }
          }
        }
        update(found.values.take(16).toList(growable: false));
      }

      _listener = emit;
      d.addListener(emit);
      emit();
      await Future<void>.delayed(const Duration(seconds: 8));
    } catch (error) {
      throw discoveryFailure(error);
    } finally {
      if (generation == _generation) await stop();
    }
  }

  @override
  Future<void> stop() async {
    _generation++;
    final d = _active;
    _active = null;
    final listener = _listener;
    _listener = null;
    if (d != null) {
      if (listener != null) d.removeListener(listener);
      try {
        await nsd.stopDiscovery(d);
      } catch (_) {
        /* No fallback polling or unhandled platform error. */
      }
    }
  }
}

// The pinned iOS plugin maps most native failures to internalError, not securityIssue.
// Explain permission recovery without falsely diagnosing every failure as a denial.
AppFailure discoveryFailure(Object error, {TargetPlatform? platform}) {
  if (error is nsd.NsdError && error.cause == nsd.ErrorCause.securityIssue) {
    return const AppFailure(
      'permission',
      'Local network access is blocked. Enable it in phone Settings, return here and retry. Manual connection also needs network permission.',
    );
  }
  if ((platform ?? defaultTargetPlatform) == TargetPlatform.iOS) {
    return const AppFailure(
      'discovery',
      'Cannot discover Family TV. Check home Wi-Fi and Remote access. On iPhone, also check Settings → Privacy & Security → Local Network → Family TV Remote, then tap Find my TV again. Manual connection also needs Local Network access.',
    );
  }
  return const AppFailure(
    'discovery',
    'Discovery is unavailable. Check home Wi-Fi and TV Remote access, or enter the TV address manually.',
  );
}
