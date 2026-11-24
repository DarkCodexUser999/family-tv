import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/core/api/apps_api.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/features/apps/hidden_editor.dart';

import 'widget_test.dart' as f;

const sony = 'com.onemainstream.sonyliv.android';

class FakeHidden implements HiddenApi {
  HiddenDocument current = HiddenDocument(0, [sony]);
  int writes = 0;
  Completer<void>? pending;
  @override
  Future<HiddenCatalog> load(PairCredential c) async => HiddenCatalog(current, [
    TvApp.fromJson({
      'package': 'tv.youtube',
      'label': 'YouTube',
      'hidden': false,
    }),
    TvApp.fromJson({'package': sony, 'label': 'Sony LIV', 'hidden': false}),
  ]);
  @override
  Future<HiddenDocument> save(PairCredential c, HiddenDocument draft) async {
    writes++;
    if (draft.revision != current.revision) {
      throw const AppFailure('conflict', 'Changed by another phone');
    }
    await pending?.future;
    return current = HiddenDocument(current.revision + 1, draft.packages);
  }

  @override
  void cancel() {}
}

class MissingHidden extends FakeHidden {
  @override
  Future<HiddenCatalog> load(PairCredential c) async {
    final value = await super.load(c);
    return HiddenCatalog(
      value.document,
      value.apps.where((a) => a.package != sony).toList(),
    );
  }
}

void main() {
  test(
    'default Sony preserved, normal hide and explicit unhide are reversible',
    () async {
      final api = FakeHidden();
      final editor = HiddenController(api, f.credential());
      await editor.load();
      expect(editor.draft.contains(sony), true);
      editor.toggle('tv.youtube', true);
      await editor.save();
      expect(api.current.packages, {sony, 'tv.youtube'});
      editor.toggle(sony, false);
      await editor.save();
      expect(api.current.packages, {'tv.youtube'});
      editor.dispose();
    },
  );
  test('conflict keeps draft and never retries without reload', () async {
    final api = FakeHidden();
    final c = HiddenController(api, f.credential());
    await c.load();
    c.toggle('tv.youtube', true);
    api.current = HiddenDocument(1, []);
    await c.save();
    expect(c.error?.code, 'conflict');
    expect(c.dirty, true);
    expect(c.canSave, false);
    await c.save();
    await c.load();
    expect(api.writes, 1);
    expect(c.draft.contains('tv.youtube'), true);
    await c.load(discard: true);
    expect(c.draft, isEmpty);
    c.dispose();
  });
  test('background uncertain save keeps draft with no replay', () async {
    final api = FakeHidden()..pending = Completer<void>();
    final c = HiddenController(api, f.credential());
    await c.load();
    c.toggle('tv.youtube', true);
    final save = c.save();
    c.suspend();
    api.pending!.complete();
    await save;
    c.resume();
    expect(c.dirty, true);
    expect(c.canSave, false);
    expect(api.writes, 1);
    c.dispose();
  });
  test('invalid and duplicate package documents rejected', () {
    expect(
      () => HiddenDocument(0, ['tv.a', 'tv.a']),
      throwsA(isA<AppFailure>()),
    );
    expect(() => HiddenDocument(0, ['a;command']), throwsA(isA<AppFailure>()));
  });
  testWidgets(
    'Sony unhide requires explicit named confirmation, cancel does not save',
    (tester) async {
      final api = FakeHidden();
      final editor = HiddenController(api, f.credential());
      await tester.pumpWidget(
        MaterialApp(home: HiddenEditor(controller: editor)),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(SwitchListTile, 'Sony LIV'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Show Sony LIV'), findsOneWidget);
      expect(api.writes, 0);
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(editor.dirty, true);
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Apply'));
      await tester.pumpAndSettle();
      expect(api.current.packages, isEmpty);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('search and large text stay usable', (tester) async {
    tester.platformDispatcher.textScaleFactorTestValue = 2;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    final c = HiddenController(FakeHidden(), f.credential());
    await tester.pumpWidget(MaterialApp(home: HiddenEditor(controller: c)));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField), 'Sony');
    await tester.pumpAndSettle();
    expect(find.text('YouTube'), findsNothing);
    expect(find.text('Sony LIV'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'unavailable default policy remains listed but cannot be accidentally unhidden',
    (tester) async {
      final c = HiddenController(MissingHidden(), f.credential());
      await tester.pumpWidget(MaterialApp(home: HiddenEditor(controller: c)));
      await tester.pumpAndSettle();
      final tile = tester.widget<SwitchListTile>(
        find.widgetWithText(SwitchListTile, 'Sony LIV'),
      );
      expect(tile.value, true);
      expect(tile.onChanged, isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
