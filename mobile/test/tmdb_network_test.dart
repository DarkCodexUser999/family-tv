import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:family_tv_remote/core/api/tmdb_client.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/features/artwork/tmdb_settings.dart';

class Headers implements HttpHeaders {
  final values = <String, String>{};
  @override
  void set(String name, Object value, {bool preserveHeaderCase = false}) {
    values[name] = value.toString();
  }

  @override
  String? value(String name) => values[name];
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class Reply extends Stream<List<int>> implements HttpClientResponse {
  Reply(this.statusCode, this.data, {int? declared})
    : contentLength = declared ?? data.length;
  @override
  final int statusCode;
  @override
  final Headers headers = Headers();
  final List<int> data;
  @override
  final int contentLength;
  @override
  StreamSubscription<List<int>> listen(
    void Function(List<int>)? onData, {
    Function? onError,
    void Function()? onDone,
    bool? cancelOnError,
  }) => Stream<List<int>>.value(data).listen(
    onData,
    onError: onError,
    onDone: onDone,
    cancelOnError: cancelOnError,
  );
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class Request implements HttpClientRequest {
  Request(this.reply);
  final Reply reply;
  @override
  final Headers headers = Headers();
  @override
  bool followRedirects = true;
  @override
  Future<HttpClientResponse> close() async => reply;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class Client implements HttpClient {
  Client(this.reply, this.calls);
  final Reply reply;
  final List<(Uri, Request)> calls;
  bool closed = false, offline = false;
  @override
  set findProxy(String Function(Uri)? value) {}
  @override
  Future<HttpClientRequest> getUrl(Uri uri) async {
    if (offline) throw const SocketException('test-only-offline');
    final request = Request(reply);
    calls.add((uri, request));
    return request;
  }

  @override
  void close({bool force = false}) {
    closed = true;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'API token stays in header; CDN request has no token and no redirects',
    () async {
      final calls = <(Uri, Request)>[];
      final replies = [
        Reply(
          200,
          utf8.encode(
            '{"images":{"secure_base_url":"https://image.tmdb.org/t/p/","backdrop_sizes":["w780"]}}',
          ),
        ),
        Reply(200, [137, 80, 78, 71, 0, 0, 0, 0]),
      ];
      final client = TmdbClient(
        createClient: () => Client(replies.removeAt(0), calls),
      );
      await client.image(
        'test-only-secret',
        const TmdbChoice('M', '/safe.jpg'),
      );
      expect(calls[0].$1.host, 'api.themoviedb.org');
      expect(
        calls[0].$2.headers.values['Authorization'],
        'Bearer test-only-secret',
      );
      expect(calls[1].$1.host, 'image.tmdb.org');
      expect(calls[1].$2.headers.values.isEmpty, true);
      expect(
        calls.every(
          (v) => !v.$2.followRedirects && !v.$1.toString().contains('secret'),
        ),
        true,
      );
    },
  );
  test(
    '401, redirect and oversized responses fail without provider body leaks',
    () async {
      for (final reply in [
        Reply(401, utf8.encode('private-provider-body')),
        Reply(302, []),
        Reply(200, [], declared: 300000),
        Reply(200, List.filled(262145, 1)),
      ]) {
        final client = TmdbClient(createClient: () => Client(reply, []));
        await expectLater(
          client.search('test-only-secret', 'Movie'),
          throwsA(
            isA<AppFailure>().having(
              (e) => e.message.contains('private-provider-body'),
              'safe error',
              false,
            ),
          ),
        );
      }
    },
  );
  test('429 cooldown prevents another network call', () async {
    final calls = <(Uri, Request)>[];
    final client = TmdbClient(
      createClient: () => Client(Reply(429, []), calls),
    );
    for (var i = 0; i < 2; i++) {
      await expectLater(
        client.search('test-only-secret', 'Movie'),
        throwsA(isA<AppFailure>().having((e) => e.code, 'code', 'rate_limit')),
      );
    }
    expect(calls.length, 1);
  });
  test('offline and malformed JSON do not expose raw exceptions', () async {
    final client = TmdbClient(
      createClient: () => Client(Reply(200, []), [])..offline = true,
    );
    await expectLater(
      client.search('test-only-secret', 'Movie'),
      throwsA(isA<AppFailure>().having((e) => e.code, 'code', 'network')),
    );
    await expectLater(
      TmdbClient(
        createClient: () => Client(Reply(200, utf8.encode('not json')), []),
      ).search('test-only-secret', 'Movie'),
      throwsA(isA<AppFailure>().having((e) => e.code, 'code', 'protocol')),
    );
  });
  test(
    'token storage is separate, device-bound, removable and verified',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.iOS;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      const channel = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      final values = <String, String>{};
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            final args = call.arguments as Map;
            switch (call.method) {
              case 'write':
                values[args['key']] = args['value'];
                return null;
              case 'read':
                return values[args['key']];
              case 'delete':
                values.remove(args['key']);
                return null;
            }
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      const store = TmdbSettings();
      await store.save('test-only-read-token-12345');
      expect(await store.load(), 'test-only-read-token-12345');
      expect(values.keys, [TmdbSettings.key]);
      expect(
        (calls.first.arguments as Map)['options']['synchronizable'],
        'false',
      );
      await store.remove();
      expect(await store.load(), null);
      await expectLater(
        store.save('invalid token'),
        throwsA(isA<AppFailure>()),
      );
    },
  );
}
