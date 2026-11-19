import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';

import '../../core/api/events.dart';
import '../../core/api/models.dart';

class RemoteController extends ChangeNotifier {
  RemoteController({required this.connector, int Function()? clock}) {
    now = clock ?? () => _clock.elapsedMilliseconds;
    final random = Random.secure();
    prefix = List.generate(
      8,
      (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
  }
  final RemoteConnector connector;
  final Stopwatch _clock = Stopwatch()..start();
  late final int Function() now;
  late final String prefix;
  RemoteSession? _session;
  StreamSubscription<String>? _subscription;
  PairCredential? _credential;
  RemoteState? state;
  Timer? _ackTimer, _initialTimer, _retryTimer;
  int _generation = 0, _sequence = 0, _anchor = 0, _retries = 0;
  bool connected = false, connecting = false, _active = true, _disposed = false;
  String? pending, lastAction;
  AppFailure? error;
  bool get canSend =>
      connected && state?.canNavigate == true && pending == null && _active;
  void _emit() {
    if (!_disposed) notifyListeners();
  }

  Future<void> connect(
    PairCredential credential, {
    bool resetRetries = true,
  }) async {
    if (_disposed || !_active || connecting) return;
    _retryTimer?.cancel();
    if (resetRetries) _retries = 0;
    final generation = ++_generation;
    _credential = credential;
    _clear();
    connecting = true;
    error = null;
    _emit();
    try {
      final session = await connector.connect(credential);
      if (generation != _generation || !_active || _disposed) {
        await session.close();
        return;
      }
      _session = session;
      _initialTimer = Timer(
        const Duration(seconds: 2),
        () => _lost(
          generation,
          const AppFailure(
            'connection',
            'The TV did not send its current state.',
          ),
        ),
      );
      final subscription = session.messages.listen(
        (value) => _message(value, generation),
        onError: (Object _) => _lost(
          generation,
          const AppFailure(
            'connection',
            'Connection lost. Previous input will not be replayed.',
          ),
        ),
        onDone: () => _lost(
          generation,
          const AppFailure(
            'connection',
            'TV disconnected. Check Remote access or Wi-Fi.',
          ),
        ),
      );
      if (generation == _generation) {
        _subscription = subscription;
      } else {
        unawaited(subscription.cancel());
      }
    } catch (failure) {
      _lost(
        generation,
        failure is AppFailure
            ? failure
            : const AppFailure('connection', 'Cannot connect to Family TV.'),
      );
    }
  }

  void _message(String text, int generation) {
    if (generation != _generation || !_active || _disposed) return;
    try {
      if (utf8.encode(text).length > 4096) throw const FormatException();
      final event = jsonDecode(text) as Map<String, dynamic>;
      if (event['v'] != 1 ||
          event['id'] is! String ||
          !(RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(event['id'] as String))) {
        throw const FormatException();
      }
      final payload = event['payload'] as Map<String, dynamic>;
      if (event['type'] == 'state') {
        state = RemoteState.fromJson(payload);
        _anchor = now();
        connected = true;
        connecting = false;
        _retries = 0;
        _initialTimer?.cancel();
      } else if (event['type'] == 'ack' && event['id'] == pending) {
        if (payload['id'] != pending ||
            payload['apiVersion'] != 1 ||
            payload['accepted'] != true) {
          throw const FormatException();
        }
        _ackTimer?.cancel();
        pending = null;
        error = null;
      } else if (event['type'] == 'error' && event['id'] == pending) {
        _ackTimer?.cancel();
        pending = null;
        final code = (payload['error'] as Map<String, dynamic>)['code'];
        if (code == 'ui_unavailable') {
          state = null;
          error = const AppFailure(
            'ui_unavailable',
            'Family TV is not ready. Use the physical remote to return to Family TV.',
          );
        } else {
          error = AppFailure(
            code is String ? code : 'input',
            'Input was not accepted. Wait for the current TV state before trying again.',
          );
        }
      } else {
        throw const FormatException();
      }
      _emit();
    } catch (_) {
      _lost(
        generation,
        const AppFailure(
          'protocol',
          'Invalid TV event. Reconnect before sending input.',
        ),
        retry: false,
      );
    }
  }

  void send(String action) {
    if (!canSend ||
        !const {
          'UP',
          'DOWN',
          'LEFT',
          'RIGHT',
          'OK',
          'BACK',
          'HOME',
          'OPTIONS',
        }.contains(action)) {
      return;
    }
    final snapshot = state!, id = '${prefix}_${++_sequence}';
    pending = id;
    lastAction = action;
    error = null;
    _emit();
    try {
      final generation = _generation;
      _ackTimer = Timer(
        const Duration(milliseconds: 450),
        () => _lost(
          generation,
          const AppFailure(
            'uncertain',
            'No reply: the previous action may have run. It will never be retried automatically.',
          ),
        ),
      );
      _session!.send(
        jsonEncode({
          'v': 1,
          'type': 'action',
          'id': id,
          'payload': {
            'id': id,
            'action': action,
            'sentAtMs': snapshot.serverTime + now() - _anchor,
            'epoch': snapshot.epoch,
          },
        }),
      );
    } catch (_) {
      _lost(
        _generation,
        const AppFailure(
          'connection',
          'Input could not be sent. It will not be replayed.',
        ),
      );
    }
  }

  void _lost(int generation, AppFailure failure, {bool retry = true}) {
    if (generation != _generation || _disposed) return;
    _generation++;
    _clear();
    error = failure;
    _emit();
    if (retry &&
        failure.code != 'unauthorized' &&
        failure.code != 'certificate' &&
        _active &&
        _retries < 3 &&
        _credential != null) {
      final delay = 1 << _retries++;
      _retryTimer = Timer(Duration(seconds: delay), () {
        if (_active && !_disposed) {
          unawaited(connect(_credential!, resetRetries: false));
        }
      });
    }
  }

  void _clear() {
    _ackTimer?.cancel();
    _initialTimer?.cancel();
    _retryTimer?.cancel();
    unawaited(_subscription?.cancel() ?? Future<void>.value());
    _subscription = null;
    final session = _session;
    _session = null;
    if (session != null) unawaited(session.close());
    connected = false;
    connecting = false;
    pending = null;
    state = null;
  }

  void suspend() {
    _active = false;
    _generation++;
    _clear();
    _emit();
  }

  void resume() {
    if (_disposed) return;
    _active = true;
    if (_credential != null) unawaited(connect(_credential!));
  }

  @override
  void dispose() {
    _disposed = true;
    _active = false;
    _generation++;
    _clear();
    super.dispose();
  }
}
