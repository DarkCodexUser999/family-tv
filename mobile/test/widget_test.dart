import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/app.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/core/api/tv_api.dart';
import 'package:family_tv_remote/core/storage/credential_store.dart';
import 'package:family_tv_remote/core/discovery/tv_discovery.dart';
import 'package:family_tv_remote/features/pairing/pairing_controller.dart';

const hash = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
PairCredential credential() => PairCredential(
  endpoint: TvEndpoint.parse('192.168.1.20:48322'),
  pinHash: hash,
  installationId: 'example-tv',
  deviceId: '11111111111111111111111111111111',
  token: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
);

class FakeStore implements CredentialStore {
  PairCredential? value;
  bool fail = false;
  @override
  Future<PairCredential?> load() async => value;
  @override
  Future<void> save(PairCredential c) async {
    if (fail) throw const AppFailure('storage', 'Storage unavailable');
    value = c;
  }

  @override
  Future<void> forget() async {
    value = null;
  }
}

class FakeApi implements TvApi {
  int requests = 0;
  AppFailure? failure;
  Completer<PairCredential>? delayed;
  @override
  Future<TvInfo> info(TvEndpoint e, String h) async => TvInfo.fromJson({
    'apiVersion': 1,
    'installationId': 'example-tv',
    'pairingOpen': true,
    'pairingSessionId': '11111111111111111111111111111111',
  });
  @override
  Future<PairCredential> pair(
    TvEndpoint e,
    String h,
    TvInfo i,
    String pin,
    String name,
  ) async {
    requests++;
    if (failure != null) throw failure!;
    return delayed == null ? credential() : delayed!.future;
  }
}

class FakeDiscovery implements TvDiscovery {
  bool denied = false, stopped = false;
  @override
  Future<void> scan(void Function(List<TvCandidate>) update) async {
    if (denied) {
      throw const AppFailure(
        'permission',
        'Local network access is blocked. Enable it in phone Settings, then retry.',
      );
    }
    update([TvCandidate('Living room', credential().endpoint)]);
  }

  @override
  Future<void> stop() async {
    stopped = true;
  }
}

void main() {
  test('addresses accept private IPv4 only, no URLs with credentials, paths or queries', () {
    for (final value in [
      '192.168.1.20:48322',
      'https://10.1.2.3:1234/v1/info',
      '172.16.0.1:8080',
    ]) {
      expect(TvEndpoint.parse(value).port, greaterThan(0));
    }
    for (final value in [
      'http://192.168.1.1:80',
      '8.8.8.8:443',
      'localhost:443',
      '127.0.0.1:443',
      '172.32.0.1:44',
      '192.168.001.1:44',
      'https://a:b@192.168.1.1:80',
      'https://192.168.1.1:80/?token=secret',
      '192.168.1.1:0',
      '192.168.1.1:99999',
    ]) {
      expect(
        () => TvEndpoint.parse(value),
        throwsA(isA<AppFailure>()),
        reason: value,
      );
    }
    expect(fingerprint(hash.toUpperCase()), hash);
    expect(() => fingerprint('too-short'), throwsA(isA<AppFailure>()));
  });
  test(
    'models reject incompatible info and do not expose token in toString',
    () {
      expect(
        () => TvInfo.fromJson({'apiVersion': 2}),
        throwsA(isA<AppFailure>()),
      );
      expect(
        () => TvInfo.fromJson({
          'apiVersion': 1,
          'installationId': 'tv',
          'pairingOpen': true,
        }),
        throwsA(isA<AppFailure>()),
      );
      expect(credential().toString(), isNot(contains(credential().token)));
      expect(
        PairCredential.fromJson(credential().toJson()).token,
        credential().token,
      );
    },
  );
  test(
    'duplicate pairing presses produce one request and save before success',
    () async {
      final api = FakeApi()..delayed = Completer<PairCredential>();
      final store = FakeStore();
      final c = PairingController(
        api: api,
        store: store,
        discovery: FakeDiscovery(),
      );
      addTearDown(c.dispose);
      await c.checkTv('192.168.1.20:48322', hash);
      final first = c.pair('123456', 'Phone');
      await c.pair('123456', 'Phone');
      expect(api.requests, 1);
      expect(c.busy, true);
      api.delayed!.complete(credential());
      await first;
      expect(c.phase, PairPhase.paired);
      expect(store.value, isNotNull);
    },
  );
  test('failed storage never displays successful saved pairing', () async {
    final store = FakeStore()..fail = true;
    final c = PairingController(
      api: FakeApi(),
      store: store,
      discovery: FakeDiscovery(),
    );
    addTearDown(c.dispose);
    await c.checkTv('192.168.1.20:48322', hash);
    await c.pair('123456', 'Phone');
    expect(c.phase, PairPhase.pin);
    expect(c.error?.code, 'storage');
    expect(c.credential, isNull);
  });
  testWidgets('first-run, discovery and permission recovery remain usable', (
    tester,
  ) async {
    final d = FakeDiscovery()..denied = true;
    final c = PairingController(
      api: FakeApi(),
      store: FakeStore(),
      discovery: d,
    );
    await tester.pumpWidget(FamilyTvApp(controller: c));
    await tester.pumpAndSettle();
    expect(find.text('Your TV. Your family.'), findsOneWidget);
    await tester.tap(find.text('Find my TV'));
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Local network access is blocked'),
      findsOneWidget,
    );
    await tester.scrollUntilVisible(
      find.widgetWithText(TextField, 'TV address and port'),
      200,
    );
    expect(
      find.widgetWithText(TextField, 'TV address and port'),
      findsOneWidget,
    );
    d.denied = false;
    await tester.ensureVisible(find.text('Find my TV'));
    await tester.tap(find.text('Find my TV'));
    await tester.pumpAndSettle();
    expect(find.text('Living room'), findsOneWidget);
  });
  testWidgets(
    'PIN failure and successful paired screen never show credentials',
    (tester) async {
      final api = FakeApi()
        ..failure = const AppFailure(
          'unauthorized',
          'Wrong PIN. Open a fresh window on TV.',
        );
      final c = PairingController(
        api: api,
        store: FakeStore(),
        discovery: FakeDiscovery(),
      );
      await tester.pumpWidget(FamilyTvApp(controller: c));
      await tester.pumpAndSettle();
      await c.checkTv('192.168.1.20:48322', hash);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, 'Six-digit TV PIN'),
        '123456',
      );
      await tester.ensureVisible(find.text('Pair this phone'));
      await tester.tap(find.text('Pair this phone'));
      await tester.pumpAndSettle();
      expect(
        find.text('Wrong PIN. Open a fresh window on TV.'),
        findsOneWidget,
      );
      api.failure = null;
      await tester.enterText(
        find.widgetWithText(TextField, 'Six-digit TV PIN'),
        '123456',
      );
      await tester.ensureVisible(find.text('Pair this phone'));
      await tester.tap(find.text('Pair this phone'));
      await tester.pumpAndSettle();
      expect(find.text('Paired with Family TV'), findsOneWidget);
      expect(find.textContaining(credential().token), findsNothing);
    },
  );
  testWidgets('restored connection makes no false reachability claim', (
    tester,
  ) async {
    final store = FakeStore()..value = credential();
    await tester.pumpWidget(
      FamilyTvApp(
        controller: PairingController(
          api: FakeApi(),
          store: store,
          discovery: FakeDiscovery(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Your saved TV'), findsOneWidget);
    expect(find.textContaining('have not been checked'), findsOneWidget);
  });
}
