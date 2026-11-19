import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'models.dart';
import 'tv_api.dart';

abstract interface class RemoteSession {
  Stream<String> get messages;
  void send(String message);
  Future<void> close();
}

abstract interface class RemoteConnector {
  Future<RemoteSession> connect(PairCredential credential);
}

class PinnedRemoteConnector implements RemoteConnector {
  @override
  Future<RemoteSession> connect(PairCredential credential) async {
    final client = PinnedTvApi.clientFor(
      credential.endpoint,
      credential.pinHash,
    );
    final uri = credential.endpoint.route('/v1/events').replace(scheme: 'wss');
    final pending = WebSocket.connect(
      uri.toString(),
      headers: {'Authorization': 'Bearer ${credential.token}'},
      compression: CompressionOptions.compressionOff,
      customClient: client,
      maxPayloadLength: 4096,
    );
    var abandoned = false;
    unawaited(
      pending
          .then((socket) async {
            if (abandoned) await socket.close();
          })
          .catchError((Object _) {}),
    );
    try {
      final socket = await pending.timeout(const Duration(seconds: 5));
      return IoRemoteSession(socket, client);
    } catch (error) {
      abandoned = true;
      client.close(force: true);
      if (error is WebSocketException && error.httpStatusCode == 401) {
        throw const AppFailure(
          'unauthorized',
          'This pairing was refused. Go back, forget the saved connection and pair again on the TV.',
        );
      }
      if (error is HandshakeException) throw networkFailure(error);
      throw const AppFailure(
        'connection',
        'Cannot connect. Check home Wi-Fi, TV Remote access and your saved pairing.',
      );
    }
  }
}

class IoRemoteSession implements RemoteSession {
  IoRemoteSession(this.socket, this.client);
  final WebSocket socket;
  final HttpClient client;
  bool closed = false;
  @override
  Stream<String> get messages => socket.map((value) {
    if (value is! String || utf8.encode(value).length > 4096) {
      throw const AppFailure('protocol', 'Invalid TV event.');
    }
    return value;
  });
  @override
  void send(String message) {
    if (closed || utf8.encode(message).length > 4096) {
      throw const AppFailure('connection', 'Connection is closed.');
    }
    socket.add(message);
  }

  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    try {
      await socket.close().timeout(const Duration(seconds: 2));
    } catch (_) {
    } finally {
      client.close(force: true);
    }
  }
}

class RemoteState {
  RemoteState.fromJson(Map<String, dynamic> value)
    : foreground = value['foreground'] == true,
      canNavigate = value['canNavigate'] == true,
      epoch = value['epoch'] as int,
      serverTime = value['serverTimeMs'] as int {
    if (value['foreground'] is! bool ||
        value['canNavigate'] is! bool ||
        canNavigate && !foreground ||
        epoch < 0 ||
        epoch.isOdd ||
        serverTime < 0 ||
        serverTime > 999999999999999 ||
        epoch > 999999999999999 ||
        value.length != 5 ||
        (value['package'] != null &&
            (value['package'] is! String ||
                !RegExp(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$')
                    .hasMatch(value['package'] as String)))) {
      throw const FormatException();
    }
  }
  final bool foreground, canNavigate;
  final int epoch, serverTime;
}
