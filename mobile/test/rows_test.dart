import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/core/api/apps_api.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/core/api/rows_api.dart';
import 'package:family_tv_remote/features/rows/rows_controller.dart';
import 'package:family_tv_remote/features/rows/rows_screen.dart';

import 'widget_test.dart' as f;
import 'apps_test.dart' as a;

import 'package:family_tv_remote/features/apps/apps_controller.dart';
import 'package:family_tv_remote/features/apps/apps_screen.dart';

List<TvApp> known() => List.generate(
  8,
  (i) => TvApp.fromJson({
    'package': 'tv.app$i',
    'label': 'App $i',
    'hidden': false,
  }),
);

class FakeRows implements RowsApi {
  RowsDocument current = RowsDocument(0, [
    ['tv.app0', 'tv.app1'],
    [],
  ]);
  AppFailure? failure;
  Completer<void>? pending;
  int writes = 0, cancels = 0, reads = 0;
  @override
  Future<RowsDocument> load(PairCredential c) async {
    reads++;
    if (failure != null) throw failure!;
    return current;
  }

  @override
  Future<RowsDocument> save(PairCredential c, RowsDocument draft) async {
    writes++;
    if (failure != null) throw failure!;
    if (draft.revision != current.revision) {
      throw const AppFailure('conflict', 'Rows changed on TV');
    }
    await pending?.future;
    return current = RowsDocument(current.revision + 1, draft.rows);
  }

  @override
  void cancel() {
    cancels++;
  }
}

void main() {
  test(
    'reorder, move, unassign and row ordering preserve all app choices',
    () async {
      final c = RowsController(FakeRows(), f.credential(), known());
      await c.load();
      c.reorder(0, 1, -1);
      expect(c.rows.first, ['tv.app1', 'tv.app0']);
      c.move(0, 0, 1);
      expect(c.rows[1], ['tv.app1']);
      c.reorderRow(1, -1);
      expect(c.rows.first, ['tv.app1']);
      c.unassign(0, 0);
      expect(c.unassigned.any((a) => a.package == 'tv.app1'), true);
      c.add(0, 'tv.app1');
      c.add(0, 'tv.app1');
      expect(c.rows.first, ['tv.app1']);
      await c.save();
      expect(c.dirty, false);
      expect(c.base!.revision, 1);
      c.dispose();
    },
  );
  test(
    'four rows and six tiles per row, duplicate and unknown additions blocked',
    () async {
      final c = RowsController(FakeRows(), f.credential(), known());
      await c.load();
      for (int i = 2; i < 8; i++) {
        c.add(0, 'tv.app$i');
      }
      expect(c.rows.first.length, 6);
      c.add(1, 'tv.unknown');
      expect(c.rows[1], isEmpty);
      for (int i = 0; i < 5; i++) {
        c.addRow();
      }
      expect(c.rows.length, 4);
      expect(
        () => RowsDocument(0, [
          ['tv.a'],
          ['tv.a'],
        ]),
        throwsA(isA<AppFailure>()),
      );
      expect(() => RowsDocument(0, []), throwsA(isA<AppFailure>()));
      c.dispose();
    },
  );
  test(
    'conflict keeps draft until explicit reload, without silently retrying',
    () async {
      final api = FakeRows();
      final editor = RowsController(api, f.credential(), known());
      await editor.load();
      editor.reorder(0, 1, -1);
      api.current = RowsDocument(1, [
        ['tv.app0'],
        ['tv.app1'],
      ]);
      await editor.save();
      expect(editor.error?.code, 'conflict');
      expect(editor.rows.first.first, 'tv.app1');
      expect(editor.canSave, false);
      await editor.save();
      expect(api.writes, 1);
      await editor.load();
      expect(editor.dirty, true);
      expect(api.reads, 1);
      await editor.load(discard: true);
      expect(editor.rows, api.current.rows);
      expect(editor.dirty, false);
      editor.dispose();
    },
  );
  test(
    'one pending save, no replay after uncertain background result',
    () async {
      final api = FakeRows()..pending = Completer<void>();
      final c = RowsController(api, f.credential(), known());
      await c.load();
      c.reorder(0, 1, -1);
      final save = c.save();
      await c.save();
      expect(api.writes, 1);
      c.suspend();
      api.pending!.complete();
      await save;
      c.resume();
      expect(c.dirty, true);
      expect(c.canSave, false);
      expect(c.message, contains('uncertain'));
      expect(api.writes, 1);
      await c.load(discard: true);
      expect(c.base!.revision, 1);
      c.dispose();
    },
  );
  test(
    'revoke or disk failure keeps draft and requires deliberate recovery',
    () async {
      for (final code in ['unauthorized', 'unavailable']) {
        final api = FakeRows();
        final editor = RowsController(api, f.credential(), known());
        await editor.load();
        editor.reorder(0, 1, -1);
        api.failure = AppFailure(code, 'Cannot save');
        await editor.save();
        expect(editor.dirty, true);
        expect(editor.canSave, false);
        expect(editor.rows.first.first, 'tv.app1');
        editor.dispose();
      }
    },
  );
  testWidgets('phone reorders and picks an unassigned app before saving', (
    tester,
  ) async {
    final api = FakeRows();
    final editor = RowsController(api, f.credential(), known());
    await tester.pumpWidget(MaterialApp(home: RowsScreen(controller: editor)));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Move App 1 earlier'));
    await tester.pumpAndSettle();
    expect(editor.rows.first.first, 'tv.app1');
    await tester.ensureVisible(find.text('Add app').first);
    await tester.tap(find.text('Add app').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('App 2'));
    await tester.pumpAndSettle();
    expect(editor.rows.first, contains('tv.app2'));
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();
    expect(api.writes, 1);
    expect(find.textContaining('Rows saved'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('unsaved exit and reload both require explicit discard', (
    tester,
  ) async {
    final c = RowsController(FakeRows(), f.credential(), known());
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: FilledButton(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => RowsScreen(controller: c)),
              ),
              child: const Text('Edit rows'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Edit rows'));
    await tester.pumpAndSettle();
    c.reorder(0, 1, -1);
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.text('Discard unsaved changes?'), findsOneWidget);
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    expect(c.dirty, true);
    await tester.tap(find.text('Reload TV rows'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Reload from TV'));
    await tester.pumpAndSettle();
    expect(c.dirty, false);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.text('Edit rows'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('large text editor remains scrollable without overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final c = RowsController(FakeRows(), f.credential(), known());
    await tester.pumpWidget(MaterialApp(home: RowsScreen(controller: c)));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(find.text('Add row'), 300);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'app grid opens rows, moves app and refreshes catalog on return',
    (tester) async {
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      final appsApi = a.FakeApps(), rowsApi = FakeRows();
      rowsApi.current = RowsDocument(0, [
        ['tv.youtube'],
        [],
      ]);
      final apps = AppsController(appsApi, f.credential());
      await tester.pumpWidget(
        MaterialApp(
          home: AppsScreen(controller: apps, rowsFactory: () => rowsApi),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Edit TV rows'));
      await tester.pumpAndSettle();
      expect(apps.active, false);
      expect(find.text('TV rows'), findsOneWidget);
      await tester.tap(find.byTooltip('Move app to row'));
      await tester.pumpAndSettle();
      await tester.tap(
        find.byWidgetPredicate((w) => w is PopupMenuItem<int> && w.value == 1),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(rowsApi.current.rows, [
        [],
        ['tv.youtube'],
      ]);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.text('TV apps'), findsOneWidget);
      expect(appsApi.cursors.length, 2);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
