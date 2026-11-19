import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/core/api/events.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/features/remote/remote_controller.dart';
import 'package:family_tv_remote/features/remote/remote_screen.dart';

import 'widget_test.dart' as fixtures;

class FakeSession implements RemoteSession {
  final stream = StreamController<String>.broadcast(sync: true);
  final sent = <String>[];
  bool closed = false, immediateAck = false;
  @override
  Stream<String> get messages => stream.stream;
  @override
  Future<void> close() async {
    closed = true;
  }

  @override
  void send(String value) {
    sent.add(value);
    if (immediateAck) ack();
  }

  void state({bool active = true, bool navigate = true, int epoch = 2}) =>
      stream.add(
        jsonEncode({
          'v': 1,
          'type': 'state',
          'id': 'state1',
          'payload': {
            'foreground': active,
            'canNavigate': navigate,
            'package': null,
            'epoch': epoch,
            'serverTimeMs': 5000,
          },
        }),
      );
  void ack() {
    final id = jsonDecode(sent.last)['id'];
    stream.add(
      jsonEncode({
        'v': 1,
        'type': 'ack',
        'id': id,
        'payload': {'apiVersion': 1, 'id': id, 'accepted': true},
      }),
    );
  }
}

class FakeConnector implements RemoteConnector {
  final sessions = <FakeSession>[];
  bool fail = false;
  @override
  Future<RemoteSession> connect(PairCredential c) async {
    if (fail) {
      sessions.add(FakeSession());
      throw const AppFailure('connection', 'Offline');
    }
    final value = FakeSession();
    sessions.add(value);
    return value;
  }

  Future<void> release() async {
    for (final s in sessions) {
      await s.stream.close();
    }
  }
}

void main() {
  testWidgets(
    'one outstanding command, immediate ACK and no replay after reconnect',
    (tester) async {
      final link = FakeConnector();
      final c = RemoteController(connector: link, clock: () => 1000);
      addTearDown(c.dispose);
      addTearDown(link.release);
      await c.connect(fixtures.credential());
      final first = link.sessions.last;
      first.state();
      c.send('OK');
      c.send('OK');
      expect(first.sent.length, 1);
      expect(c.canSend, false);
      first.ack();
      expect(c.canSend, true);
      first.immediateAck = true;
      c.send('RIGHT');
      await tester.pump(const Duration(milliseconds: 500));
      expect(c.connected, true);
      c.suspend();
      expect(first.closed, true);
      c.resume();
      await tester.pump();
      final fresh = link.sessions.last;
      expect(fresh, same(link.sessions[1]));
      expect(fresh.sent, isEmpty);
      c.send('OK');
      expect(fresh.sent, isEmpty);
      fresh.state(epoch: 4);
      c.send('LEFT');
      expect(jsonDecode(fresh.sent.single)['payload']['epoch'], 4);
      expect(fresh.sent.single, isNot(first.sent.first));
    },
  );
  testWidgets(
    'ACK timeout closes connection and background cancels every retry',
    (tester) async {
      final link = FakeConnector();
      final c = RemoteController(connector: link);
      addTearDown(c.dispose);
      addTearDown(link.release);
      await c.connect(fixtures.credential());
      final s = link.sessions.single;
      s.state();
      c.send('OK');
      await tester.pump(const Duration(milliseconds: 451));
      expect(c.pending, isNull);
      expect(s.closed, true);
      expect(c.error?.code, 'uncertain');
      c.suspend();
      await tester.pump(const Duration(seconds: 15));
      expect(link.sessions.length, 1);
    },
  );
  testWidgets('reconnect backoff is finite without an initial TV state', (
    tester,
  ) async {
    final link = FakeConnector()..fail = true;
    final c = RemoteController(connector: link);
    addTearDown(c.dispose);
    addTearDown(link.release);
    await c.connect(fixtures.credential());
    await tester.pump(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 2));
    await tester.pump(const Duration(seconds: 4));
    await tester.pump(const Duration(seconds: 20));
    expect(link.sessions.length, 4);
    expect(c.connected, false);
  });
  testWidgets(
    'background state blocks HOME and late session data is discarded',
    (tester) async {
      final link = FakeConnector();
      final c = RemoteController(connector: link);
      addTearDown(c.dispose);
      addTearDown(link.release);
      await c.connect(fixtures.credential());
      final s = link.sessions.single;
      s.state(active: false, navigate: false);
      c.send('HOME');
      expect(s.sent, isEmpty);
      c.suspend();
      s.state();
      expect(c.canSend, false);
    },
  );
  testWidgets('malformed event closes session without automatic replay', (
    tester,
  ) async {
    final link = FakeConnector();
    final c = RemoteController(connector: link);
    addTearDown(c.dispose);
    addTearDown(link.release);
    await c.connect(fixtures.credential());
    link.sessions.single.stream.add('{"v":99}');
    expect(c.connected, false);
    expect(c.error?.code, 'protocol');
    await tester.pump(const Duration(seconds: 10));
    expect(link.sessions.length, 1);
  });
  testWidgets(
    'pad provides feedback and disables all controls outside Family TV',
    (tester) async {
      final link = FakeConnector();
      final c = RemoteController(connector: link);
      addTearDown(link.release);
      await tester.pumpWidget(
        MaterialApp(
          home: RemoteScreen(credential: fixtures.credential(), controller: c),
        ),
      );
      await tester.pump();
      final s = link.sessions.single;
      s.state();
      await tester.pump();
      await tester.tap(find.text('OK'));
      await tester.pump();
      expect(find.text('Sent OK…'), findsOneWidget);
      expect(s.sent.length, 1);
      s.ack();
      s.state(active: false, navigate: false, epoch: 4);
      await tester.pump();
      final ok = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'OK'),
      );
      expect(ok.onPressed, isNull);
      expect(find.textContaining('Use the physical remote'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
