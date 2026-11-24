// Host checks of shared iOS branches/channel arguments; no real Keychain/Bonjour claim.
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/app.dart';
import 'package:family_tv_remote/core/discovery/tv_discovery.dart';
import 'package:family_tv_remote/core/storage/credential_store.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/features/pairing/pairing_controller.dart';
import 'package:nsd/nsd.dart' as nsd;

import 'widget_test.dart' as f;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() => debugDefaultTargetPlatformOverride = null);
  test('iOS internal discovery errors give permission recovery without inventing denial', () {
    final error = discoveryFailure(Exception(), platform: TargetPlatform.iOS);
    expect(error.code, 'discovery');
    expect(error.message, contains('Local Network'));
    final denied = discoveryFailure(
      nsd.NsdError(nsd.ErrorCause.securityIssue, 'denied'),
      platform: TargetPlatform.iOS,
    );
    expect(denied.code, 'permission');
    expect(
      discoveryFailure(Exception(), platform: TargetPlatform.android).message,
      contains('manually'),
    );
  });
  test('production credential store uses device-only non-syncing iOS options and read-back verification', () async {
    debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
    const channel = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    String? saved;
    bool locked = false;
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          if (locked) {
            throw PlatformException(
              code: 'locked',
              message: 'private native details',
            );
          }
          final args = call.arguments as Map;
          final options = args['options'] as Map;
          expect(options['accessibility'], 'first_unlock_this_device');
          expect(options['synchronizable'], 'false');
          switch (call.method) {
            case 'write':
              saved = args['value'] as String;
              return null;
            case 'read':
              return saved;
            case 'delete':
              saved = null;
              return null;
          }
          throw StateError('Unexpected storage operation');
        });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, null),
    );
    final store = SecureCredentialStore();
    await store.save(f.credential());
    expect(calls.map((e) => e.method).take(2), ['write', 'read']);
    expect(
      (await SecureCredentialStore().load())!.endpoint.toString(),
      f.credential().endpoint.toString(),
    );
    locked = true;
    await expectLater(
      store.load(),
      throwsA(
        isA<AppFailure>().having(
          (e) => e.message,
          'message',
          isNot(contains('private native details')),
        ),
      ),
    );
    locked = false;
    saved = '{broken';
    await expectLater(
      store.load(),
      throwsA(isA<AppFailure>().having((e) => e.code, 'code', 'storage')),
    );
    await store.forget();
    expect(await store.load(), isNull);
  });
  testWidgets(
    'iPhone consent explanation precedes user-started discovery and manual recovery',
    (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final c = PairingController(
        api: f.FakeApi(),
        store: f.FakeStore(),
        discovery: f.FakeDiscovery(),
      );
      await tester.pumpWidget(FamilyTvApp(controller: c));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('On iPhone, allow Local Network'),
        findsOneWidget,
      );
      expect(c.scanning, false);
      await tester.ensureVisible(find.text('Find my TV'));
      await tester.tap(find.text('Find my TV'));
      await tester.pumpAndSettle();
      expect(find.text('Living room'), findsOneWidget);
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
      await tester.pumpAndSettle();
      expect(c.scanning, false); // Explicit retry; no permission polling.
      await tester.pumpWidget(const SizedBox());
    },
  );
}
