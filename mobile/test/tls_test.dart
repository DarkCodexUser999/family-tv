import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/core/api/tv_api.dart';

// Test-only socket routing: production private endpoint validation stays unchanged.
class LoopbackRoute extends HttpOverrides {
  LoopbackRoute(this.port);
  final int port;
  @override
  HttpClient createHttpClient(SecurityContext? context) =>
      RoutedClient(super.createHttpClient(context), context, port);
}

// HttpClient connection factories return an already-secure socket for HTTPS.
// Preserve the production callback/context; only redirect the TCP destination.
class RoutedClient implements HttpClient {
  RoutedClient(this.client, this.context, this.port);
  final HttpClient client;
  final SecurityContext? context;
  final int port;
  bool Function(X509Certificate, String, int)? check;
  @override
  set connectionTimeout(Duration? timeout) {
    client.connectionTimeout = timeout;
  }

  @override
  set findProxy(String Function(Uri)? proxy) {
    client.findProxy = proxy;
  }

  @override
  set badCertificateCallback(
    bool Function(X509Certificate, String, int)? callback,
  ) {
    check = callback;
  }

  @override
  Future<HttpClientRequest> openUrl(String method, Uri uri) {
    client.connectionFactory = (uri, _, _) => SecureSocket.startConnect(
      InternetAddress.loopbackIPv4,
      port,
      context: context,
      onBadCertificate: (cert) =>
          check?.call(cert, uri.host, uri.port) ?? false,
    );
    return client.openUrl(method, uri);
  }

  @override
  void close({bool force = false}) {
    client.close(force: force);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnsupportedError('Unexpected HttpClient operation in test');
}

void main() {
  late HttpServer server;
  late String hash;
  late SecurityContext context;
  int requests = 0, status = 200;
  String? pairBody;
  bool huge = false;
  final api = PinnedTvApi();
  final target = TvEndpoint.parse('10.1.2.3:48322');
  setUpAll(() async {
    final dir = await Directory('build/tls-tests').create(recursive: true);
    final key = '${dir.path}/key.pem', cert = '${dir.path}/cert.pem';
    final result = await Process.run('/usr/bin/openssl', [
      'req',
      '-x509',
      '-newkey',
      'rsa:2048',
      '-nodes',
      '-keyout',
      key,
      '-out',
      cert,
      '-days',
      '1',
      '-subj',
      '/CN=FamilyTVTest',
    ]);
    if (result.exitCode != 0) {
      throw StateError('Could not generate test-only certificate');
    }
    context = SecurityContext()
      ..useCertificateChain(cert)
      ..usePrivateKey(key);
    final pem = await File(cert).readAsString();
    final encoded = pem.replaceAll(RegExp(r'-----[^-]+-----|\s'), '');
    hash = sha256.convert(base64Decode(encoded)).toString();
  });
  setUp(() async {
    requests = 0;
    status = 200;
    pairBody = null;
    huge = false;
    server = await HttpServer.bindSecure(
      InternetAddress.loopbackIPv4,
      0,
      context,
    );
    server.listen(
      (request) async {
        requests++;
        request.response.statusCode = status;
        if (status == 302) {
          request.response.headers.set(
            HttpHeaders.locationHeader,
            'https://10.9.8.7:48322/redirect',
          );
        }
        request.response.headers.contentType = ContentType.json;
        if (request.uri.path == '/v1/pair') {
          pairBody = await utf8.decodeStream(request);
          request.response.write(
            jsonEncode({
              'apiVersion': 1,
              'deviceId': '11111111111111111111111111111111',
              'token': 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
            }),
          );
        } else {
          request.response.write(
            huge
                ? 'x' * 17000
                : jsonEncode({
                    'apiVersion': 1,
                    'installationId': 'test-tv',
                    'pairingOpen': true,
                    'pairingSessionId': '11111111111111111111111111111111',
                  }),
          );
        }
        await request.response.close();
      },
      onError: (Object _) {},
    ); // Rejected handshakes are expected in negative cases.
  });
  tearDown(() async {
    await server.close(force: true);
  });
  Future<T> routed<T>(Future<T> Function() work) =>
      HttpOverrides.runWithHttpOverrides(work, LoopbackRoute(server.port));
  test(
    'matching pinned certificate permits real HTTPS info and PIN pairing',
    () async {
      await routed(() async {
        final info = await api.info(target, hash);
        expect(info.installationId, 'test-tv');
        final credential = await api.pair(
          target,
          hash,
          info,
          '123456',
          'Test phone',
        );
        expect(credential.deviceId, '11111111111111111111111111111111');
        expect(jsonDecode(pairBody!)['pin'], '123456');
        expect(requests, 2);
      });
    },
  );
  test(
    'wrong fingerprint blocks HTTP before any credentials reach server',
    () async {
      await routed(() async {
        await expectLater(
          api.info(target, '0' * 64),
          throwsA(
            isA<AppFailure>().having((e) => e.code, 'code', 'certificate'),
          ),
        );
        expect(requests, 0);
        expect(pairBody, isNull);
      });
    },
  );
  test(
    'wrong certificate also blocks the PIN submission before HTTP',
    () async {
      await routed(() async {
        final info = TvInfo.fromJson({
          'apiVersion': 1,
          'installationId': 'test-tv',
          'pairingOpen': true,
          'pairingSessionId': '11111111111111111111111111111111',
        });
        await expectLater(
          api.pair(target, '0' * 64, info, '123456', 'Test phone'),
          throwsA(isA<AppFailure>()),
        );
        expect(requests, 0);
        expect(pairBody, isNull);
      });
    },
  );
  test('oversized response is rejected', () async {
    huge = true;
    await routed(() async {
      await expectLater(
        api.info(target, hash),
        throwsA(isA<AppFailure>().having((e) => e.code, 'code', 'protocol')),
      );
    });
  });
  test('PIN and rate limit errors are safe typed messages', () async {
    await routed(() async {
      final info = await api.info(target, hash);
      status = 429;
      await expectLater(
        api.pair(target, hash, info, '123456', 'Test phone'),
        throwsA(
          isA<AppFailure>().having((e) => e.code, 'code', 'rate_limited'),
        ),
      );
    });
  });
  test('a redirect is rejected without contacting its target', () async {
    status = 302;
    await routed(() async {
      await expectLater(api.info(target, hash), throwsA(isA<AppFailure>()));
    });
    expect(requests, 1);
  });
}
