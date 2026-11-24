// Host widget walkthrough, not an Android device/emulator or secure-storage acceptance test.
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/app.dart';
import 'package:family_tv_remote/features/pairing/pairing_controller.dart';
import 'package:family_tv_remote/features/remote/remote_controller.dart';

import 'apps_test.dart' as apps;
import 'remote_test.dart' as remote;
import 'widget_test.dart' as fixtures;

void main() {
  testWidgets(
    'pair, reopen saved connection, pad, apps and return without replay',
    (tester) async {
      final store = fixtures.FakeStore();
      PairingController controller() => PairingController(
        api: fixtures.FakeApi(),
        store: store,
        discovery: fixtures.FakeDiscovery(),
      );
      final first = controller();
      await tester.pumpWidget(FamilyTvApp(controller: first));
      await tester.pumpAndSettle();
      await first.checkTv('192.168.1.20:48322', fixtures.hash);
      await first.pair('123456', 'Family phone');
      await tester.pumpAndSettle();
      expect(store.value, isNotNull);
      // New app/controller instance reads the same fake persistent store.
      await tester.pumpWidget(const SizedBox());
      final second = controller(),
          link = remote.FakeConnector(),
          api = apps.FakeApps();
      await tester.pumpWidget(
        FamilyTvApp(
          controller: second,
          remoteFactory: () => RemoteController(connector: link),
          appsFactory: () => api,
        ),
      );
      await tester.pumpAndSettle();
      expect(second.phase, PairPhase.saved);
      await tester.ensureVisible(find.text('Open remote'));
      await tester.tap(find.text('Open remote'));
      await tester.pump();
      link.sessions.last.state();
      await tester.pumpAndSettle();
      await tester.tap(find.text('OK'));
      await tester.pump();
      expect(link.sessions.last.sent.length, 1);
      link.sessions.last.ack();
      await tester.pump();
      await tester.tap(find.byTooltip('TV apps'));
      await tester.pumpAndSettle();
      expect(link.sessions.first.closed, true);
      expect(find.text('YouTube'), findsOneWidget);
      for (final state in [
        AppLifecycleState.inactive,
        AppLifecycleState.hidden,
        AppLifecycleState.paused,
        AppLifecycleState.hidden,
        AppLifecycleState.inactive,
        AppLifecycleState.resumed,
      ]) {
        tester.binding.handleAppLifecycleStateChanged(state);
      }
      await tester.pump();
      expect(link.sessions.length, 1); // Covered remote must stay suspended.
      expect(
        find
            .widgetWithText(FilledButton, 'Open')
            .evaluate()
            .every((e) => (e.widget as FilledButton).onPressed == null),
        true,
      );
      await tester.tap(find.byTooltip('Refresh apps'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Open').first);
      await tester.pumpAndSettle();
      expect(api.requests.length, 1);
      expect(find.textContaining('physical remote'), findsOneWidget);
      await tester.pageBack();
      await tester.pump();
      expect(link.sessions.length, 2);
      link.sessions.last.state();
      await tester.pumpAndSettle();
      expect(link.sessions.last.sent, isEmpty);
      expect(find.textContaining(store.value!.token), findsNothing);
      await tester.pumpWidget(const SizedBox());
      await link.release();
    },
  );
  testWidgets('changed address requires explicit forget and fresh pairing', (
    tester,
  ) async {
    final store = fixtures.FakeStore()..value = fixtures.credential();
    final c = PairingController(
      api: fixtures.FakeApi(),
      store: store,
      discovery: fixtures.FakeDiscovery(),
    );
    await tester.pumpWidget(FamilyTvApp(controller: c));
    await tester.pumpAndSettle();
    expect(c.credential!.endpoint.address, '192.168.1.20');
    await c.forget();
    await tester.pumpAndSettle();
    expect(store.value, isNull);
    expect(c.credential, isNull);
    await c.checkTv('192.168.1.30:48323', fixtures.hash);
    await tester.pumpAndSettle();
    expect(c.endpoint!.address, '192.168.1.30');
    expect(c.phase, PairPhase.pin);
    expect(
      c.credential,
      isNull,
    ); // Old bearer is never silently forwarded to a new endpoint.
    await tester.pumpWidget(const SizedBox());
  });
}
