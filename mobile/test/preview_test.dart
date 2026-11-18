import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/app.dart';
import 'package:family_tv_remote/features/pairing/pairing_controller.dart';

import 'widget_test.dart' as fixtures;

void main() {
  setUpAll(() async {
    // Use the SDK font for a readable host render, never an invented phone screenshot.
    final packages =
        jsonDecode(
              await File('.dart_tool/package_config.json').readAsString(),
            )['packages']
            as List;
    final root = Uri.parse(
      packages.firstWhere((p) => p['name'] == 'flutter')['rootUri'] as String,
    );
    final uri = Uri.parse(
      '${root.toString().replaceAll(RegExp(r'/$'), '')}/../../bin/cache/artifacts/material_fonts/Roboto-Regular.ttf',
    );
    final bytes = await File.fromUri(uri).readAsBytes();
    await (FontLoader(
      'Roboto',
    )..addFont(Future.value(ByteData.sublistView(bytes)))).load();
    final iconUri = Uri.parse(
      '${root.toString().replaceAll(RegExp(r'/$'), '')}/../../bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf',
    );
    final icons = await File.fromUri(iconUri).readAsBytes();
    await (FontLoader(
      'MaterialIcons',
    )..addFont(Future.value(ByteData.sublistView(icons)))).load();
  });
  testWidgets(
    'phone-size first-run render and large text remain scrollable without overflow',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final key = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: FamilyTvApp(
            controller: PairingController(
              api: fixtures.FakeApi(),
              store: fixtures.FakeStore(),
              discovery: fixtures.FakeDiscovery(),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        final boundary =
            key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
        final image = await boundary.toImage(pixelRatio: 2);
        final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
        await Directory('build/preview').create(recursive: true);
        await File('build/preview/first-run-host-render.png')
            .writeAsBytes(bytes!.buffer.asUint8List());
        image.dispose();
      });
      expect(tester.takeException(), isNull);
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Confirm TV and continue'),
        300,
        scrollable: find.byType(Scrollable).first,
      );
      expect(tester.takeException(), isNull);
    },
  );
}
