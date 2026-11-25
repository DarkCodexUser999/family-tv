import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/core/api/apps_api.dart';
import 'package:family_tv_remote/features/artwork/artwork_api.dart';
import 'package:family_tv_remote/features/artwork/artwork_screen.dart';

import 'widget_test.dart' as f;

class FakeArtwork extends ArtworkApi {
  FakeArtwork() : super(f.credential());
  int uploads = 0, resets = 0, cancels = 0;
  Completer<void>? pending;
  @override
  Future<void> upload(
    String pkg,
    String kind,
    Uint8List bytes,
    int x,
    int y, {
    void Function(double)? progress,
    void Function()? committing,
  }) async {
    uploads++;
    progress?.call(.5);
    if (pending != null) await pending!.future;
    committing?.call();
  }

  @override
  Future<void> reset(String pkg, String kind) async {
    resets++;
  }

  @override
  void cancel() {
    cancels++;
  }
}

const png =
    'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAC0lEQVR4XmNgQAYAAA4AAdXbrS0AAAAASUVORK5CYII=';
Future<void> screen(
  WidgetTester tester,
  FakeArtwork api, {
  bool corrupt = false,
}) async {
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(
        const MethodChannel('familytv/artwork-picker'),
        (call) async =>
            corrupt ? Uint8List.fromList([1, 2, 3]) : base64Decode(png),
      );
  addTearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('familytv/artwork-picker'),
          null,
        ),
  );
  await tester.pumpWidget(
    MaterialApp(
      home: ArtworkScreen(
        apps: [
          TvApp.fromJson({
            'package': 'tv.youtube',
            'label': 'YouTube',
            'hidden': false,
          }),
        ],
        api: api,
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('picker produces bounded crop preview and enables upload', (
    tester,
  ) async {
    final api = FakeArtwork();
    await screen(tester, api);
    final dynamic state = tester.state(find.byType(ArtworkScreen));
    await tester.runAsync(() async {
      await state.pick();
    });
    await tester.pumpAndSettle();
    expect(find.byType(RawImage), findsOneWidget);
    expect(find.byType(Slider), findsNWidgets(2));
    await tester.ensureVisible(find.text('Upload artwork'));
    await tester.tap(find.text('Upload artwork'));
    await tester.pumpAndSettle();
    expect(api.uploads, 1);
    expect(find.text('Artwork saved.'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('invalid picked data cannot be uploaded', (tester) async {
    final api = FakeArtwork();
    await screen(tester, api, corrupt: true);
    await tester.tap(find.text('Choose image'));
    await tester.pumpAndSettle();
    expect(find.byType(RawImage), findsNothing);
    expect(api.uploads, 0);
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('progress and cancel do not replay a pending upload', (
    tester,
  ) async {
    final api = FakeArtwork()..pending = Completer<void>();
    await screen(tester, api);
    final dynamic state = tester.state(find.byType(ArtworkScreen));
    await tester.runAsync(() async {
      await state.pick();
    });
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Upload artwork'));
    await tester.tap(find.text('Upload artwork'));
    await tester.pump();
    await tester.scrollUntilVisible(find.text('Cancel upload'), 150);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    await tester.tap(find.text('Cancel upload'));
    api.pending!.complete();
    await tester.pumpAndSettle();
    expect(api.uploads, 1);
    expect(
      find.text('Upload canceled. Previous artwork retained.'),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets(
    'reset requires confirmation and cancel does not change artwork',
    (tester) async {
      final api = FakeArtwork();
      await screen(tester, api);
      await tester.ensureVisible(find.text('Reset uploaded artwork'));
      await tester.tap(find.text('Reset uploaded artwork'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(api.resets, 0);
      await tester.tap(find.text('Reset uploaded artwork'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Reset'));
      await tester.pumpAndSettle();
      expect(api.resets, 1);
      expect(find.text('Uploaded artwork reset.'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    },
  );
}
