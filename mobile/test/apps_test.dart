import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/core/api/apps_api.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/features/apps/apps_controller.dart';
import 'package:family_tv_remote/features/apps/apps_screen.dart';

import 'widget_test.dart' as fixtures;

AppsPage appPage(List<String> packages, {int revision = 1, String? next}) =>
    AppsPage.fromJson({
      'apiVersion': 1,
      'revision': revision,
      'nextCursor': next,
      'apps': packages
          .map(
            (p) => {
              'package': p,
              'label': p == 'tv.youtube' ? 'YouTube' : p,
              'hidden': false,
              'iconRevision': revision,
            },
          )
          .toList(),
    });

class FakeApps implements AppsApi {
  final pages = <AppsPage>[
    appPage(['tv.youtube', 'tv.prime']),
  ];
  final cursors = <String?>[], requests = <String>[];
  AppFailure? failure;
  Completer<void>? launchPending;
  Completer<AppsPage>? pagePending;
  int canceled = 0;
  @override
  Future<AppsPage> page(PairCredential c, String? cursor) async {
    cursors.add(cursor);
    if (failure != null) throw failure!;
    return pagePending?.future ?? pages[cursor == null ? 0 : 1];
  }

  @override
  Future<Uint8List> icon(PairCredential c, String package) async =>
      throw const AppFailure('not_found', 'Missing icon');
  @override
  Future<void> launch(
    PairCredential c,
    String p,
    int revision,
    String id,
  ) async {
    requests.add('$p:$revision:$id');
    if (failure != null) throw failure!;
    await launchPending?.future;
  }

  @override
  void cancel() {
    canceled++;
  }
}

void main() {
  test('bounded pages merge, unavailable icons do not block launch', () async {
    final api = FakeApps()..pages.clear();
    api.pages.addAll([
      appPage(['tv.youtube'], next: '1_1'),
      appPage(['tv.prime']),
    ]);
    final c = AppsController(api, fixtures.credential());
    await c.refresh();
    expect(c.apps.length, 2);
    expect(api.cursors, [null, '1_1']);
    expect(c.error, isNull);
    await c.launch(c.apps.first);
    expect(api.requests.length, 1);
    expect(c.message, contains('physical remote'));
    c.dispose();
  });
  test('mixed revision and repeated package fail closed', () async {
    for (final second in [
      appPage(['tv.prime'], revision: 2),
      appPage(['tv.youtube']),
    ]) {
      final api = FakeApps()..pages.clear();
      api.pages.addAll([
        appPage(['tv.youtube'], next: '1_1'),
        second,
      ]);
      final c = AppsController(api, fixtures.credential());
      await c.refresh();
      expect(c.apps, isEmpty);
      expect(c.error?.code, 'protocol');
      c.dispose();
    }
  });
  test(
    'one pending launch and no automatic replay after Wi-Fi failure',
    () async {
      final api = FakeApps();
      final c = AppsController(api, fixtures.credential());
      await c.refresh();
      api.launchPending = Completer<void>();
      final first = c.launch(c.apps.first);
      await c.launch(c.apps.first);
      expect(api.requests.length, 1);
      api.launchPending!.completeError(const AppFailure('network', 'Offline'));
      await first;
      expect(c.pending, isNull);
      expect(api.requests.length, 1);
      expect(c.message, contains('No automatic retry'));
      c.dispose();
    },
  );
  test(
    'late results after backgrounding are ignored; resume is explicit',
    () async {
      final api = FakeApps()..pagePending = Completer<AppsPage>();
      final c = AppsController(api, fixtures.credential());
      final refresh = c.refresh();
      c.suspend();
      api.pagePending!.complete(appPage(['tv.youtube']));
      await refresh;
      expect(c.apps, isEmpty);
      expect(c.active, false);
      expect(api.canceled, greaterThan(1));
      c.resume();
      api.pagePending = null;
      await c.refresh();
      expect(c.apps.length, 2);
      c.dispose();
    },
  );
  test('revocation is actionable and rejected targets never replay', () async {
    final api = FakeApps();
    final c = AppsController(api, fixtures.credential());
    await c.refresh();
    api.failure = const AppFailure('unauthorized', 'Pair again');
    await c.launch(c.apps.first);
    expect(c.error?.code, 'unauthorized');
    expect(api.requests.length, 1);
    c.dispose();
  });
  test('hidden and oversized pages are rejected', () {
    expect(
      () => AppsPage.fromJson({
        'apiVersion': 1,
        'revision': 1,
        'nextCursor': null,
        'apps': [
          {'package': 'tv.hidden', 'label': 'Hidden', 'hidden': true},
        ],
      }),
      throwsA(isA<AppFailure>()),
    );
    expect(
      () => appPage(List.generate(26, (i) => 'tv.app$i')),
      throwsA(isA<AppFailure>()),
    );
  });
  testWidgets('searchable grid opens app and exposes handoff', (tester) async {
    final c = AppsController(FakeApps(), fixtures.credential());
    await tester.pumpWidget(MaterialApp(home: AppsScreen(controller: c)));
    await tester.pumpAndSettle();
    expect(find.text('YouTube'), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'you');
    await tester.pumpAndSettle();
    expect(find.text('tv.prime'), findsNothing);
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.textContaining('physical remote'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('large text grid stays usable with empty/error recovery', (
    tester,
  ) async {
    final api = FakeApps()
      ..failure = const AppFailure('network', 'Check home Wi-Fi');
    final c = AppsController(api, fixtures.credential());
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(360, 640),
            textScaler: TextScaler.linear(2),
          ),
          child: AppsScreen(controller: c),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Check home Wi-Fi'), findsOneWidget);
    api.failure = null;
    await tester.tap(find.byTooltip('Refresh apps'));
    await tester.pumpAndSettle();
    expect(find.text('YouTube'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  test(
    'failed metadata refresh cannot enable row editing from an old catalog',
    () async {
      final api = FakeApps();
      final c = AppsController(api, fixtures.credential());
      await c.refresh();
      expect(c.needsRefresh, false);
      api.failure = const AppFailure('network', 'Offline');
      await c.refresh();
      expect(c.needsRefresh, true);
      expect(c.apps, isEmpty);
      c.dispose();
    },
  );
}
