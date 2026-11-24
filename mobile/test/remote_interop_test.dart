import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/core/api/apps_api.dart';
import 'package:family_tv_remote/core/api/events.dart';
import 'package:family_tv_remote/features/remote/remote_controller.dart';

class RealHttp extends HttpOverrides {}

Future<void> until(RemoteController c, bool Function() predicate) async {
  if (predicate()) return;
  final done = Completer<void>();
  void changed() {
    if (predicate() && !done.isCompleted) done.complete();
  }

  c.addListener(changed);
  try {
    await done.future.timeout(const Duration(seconds: 8));
  } finally {
    c.removeListener(changed);
  }
}

void main() {
  test('Dart pinned WSS client interoperates with the actual Java transport and foreground gate', () async {
    final addresses = [
      for (final n in await NetworkInterface.list(
        type: InternetAddressType.IPv4,
      ))
        ...n.addresses,
    ].where((ip) => TvEndpoint.privateAddress(ip.address)).toList();
    if (addresses.isEmpty) {
      if (Platform.environment['FAMILYTV_REQUIRE_INTEROP'] == '1') {
        throw StateError('CI interop requires a private IPv4 interface');
      }
      markTestSkipped('No private IPv4 interface for host-only interop');
      return;
    }
    final host = addresses.first.address;
    final javaHome = Platform.environment['JAVA_HOME'];
    if (javaHome == null) {
      throw StateError('Run scripts/check-remote-interop.sh with JDK 17');
    }
    final root = Directory.current.parent.path;
    final out = File('build/interop-actions.txt');
    await out.parent.create(recursive: true);
    final server = await Process.start('$javaHome/bin/java', [
      '-cp',
      '$root/launcher/build/lan-check/classes${Platform.isWindows ? ';' : ':'}$root/launcher/libs/nanohttpd-2.3.1.jar',
      'app.firetvremote.launcher.lan.RemoteHarness',
      '$root/launcher/build/lan-check/test.p12',
      host,
      out.absolute.path,
    ]);
    final errors = StringBuffer();
    final stderr = server.stderr.transform(utf8.decoder).listen(errors.write);
    final lines = StreamIterator(
      server.stdout.transform(utf8.decoder).transform(const LineSplitter()),
    );
    try {
      if (!await lines.moveNext().timeout(const Duration(seconds: 5))) {
        throw StateError('Host harness did not start: $errors');
      }
      final info = jsonDecode(lines.current) as Map<String, dynamic>;
      final credential = PairCredential(
        endpoint: TvEndpoint.parse('$host:${info['port']}'),
        pinHash: info['fingerprint'] as String,
        installationId: 'host-interop',
        deviceId: '11111111111111111111111111111111',
        token: 'c' * 64,
      );
      await HttpOverrides.runWithHttpOverrides(() async {
        final c = RemoteController(connector: PinnedRemoteConnector());
        try {
          await c.connect(credential);
          await until(c, () => c.canSend);
          c.send('RIGHT');
          await until(c, () => c.pending == null);
          expect(await out.readAsLines(), ['RIGHT']);
          server.stdin.writeln('pause');
          await server.stdin.flush();
          await until(c, () => c.state?.foreground == false);
          c.send('HOME');
          expect(await out.readAsLines(), ['RIGHT']);
          server.stdin.writeln('resume');
          await server.stdin.flush();
          await until(c, () => c.canSend);
          c.send('OK');
          await until(c, () => c.pending == null);
          expect(await out.readAsLines(), ['RIGHT', 'OK']);
          final apps = PinnedAppsApi();
          try {
            final page = await apps.page(credential, null);
            expect(page.apps.single.package, 'tv.youtube');
            expect(
              (await apps.icon(credential, 'tv.youtube')).length,
              lessThan(8193),
            );
            await apps.launch(
              credential,
              'tv.youtube',
              page.revision,
              'interop-open',
            );
            await apps.launch(
              credential,
              'tv.youtube',
              page.revision,
              'interop-open',
            );
            expect(await out.readAsLines(), [
              'RIGHT',
              'OK',
              'LAUNCH:tv.youtube',
            ]);
            await expectLater(
              apps.launch(
                credential,
                'tv.hidden',
                page.revision,
                'hidden-open',
              ),
              throwsA(isA<AppFailure>()),
            );
            await expectLater(
              apps.launch(
                credential,
                'tv.youtube',
                page.revision - 1,
                'old-open',
              ),
              throwsA(isA<AppFailure>()),
            );
          } finally {
            apps.cancel();
          }
          server.stdin.writeln('revoke');
          await server.stdin.flush();
          await until(c, () => !c.connected);
          expect(c.canSend, false);
          final revoked = PinnedAppsApi();
          try {
            await expectLater(
              revoked.page(credential, null),
              throwsA(
                isA<AppFailure>().having((e) => e.code, 'code', 'unauthorized'),
              ),
            );
          } finally {
            revoked.cancel();
          }
        } finally {
          c.dispose();
        }
      }, RealHttp());
    } finally {
      try {
        server.stdin.writeln('stop');
        await server.stdin.flush();
        await server.stdin.close();
      } catch (_) {}
      try {
        await server.exitCode.timeout(const Duration(seconds: 4));
      } catch (_) {
        server.kill();
      }
      await lines.cancel();
      await stderr.cancel();
    }
  });
}
