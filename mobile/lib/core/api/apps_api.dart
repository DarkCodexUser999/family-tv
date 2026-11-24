import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'models.dart';
import 'tv_api.dart';

class TvApp {
  TvApp.fromJson(Map<String, dynamic> value)
    : package = value['package'] as String,
      label = value['label'] as String {
    if (!RegExp(r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$')
            .hasMatch(package) ||
        package.length > 160 ||
        label.isEmpty ||
        label.length > 80 ||
        value['hidden'] != false) {
      throw const AppFailure(
        'protocol',
        'Invalid app catalog. Refresh the list.',
      );
    }
  }
  final String package, label;
}

class AppsPage {
  AppsPage.fromJson(Map<String, dynamic> value)
    : revision = value['revision'] as int,
      next = value['nextCursor'] as String?,
      apps = (value['apps'] as List)
          .map((e) => TvApp.fromJson(e as Map<String, dynamic>))
          .toList() {
    if (value['apiVersion'] != 1 ||
        revision < 0 ||
        revision > 999999999999999 ||
        apps.length > 25 ||
        next != null && !RegExp(r'^[0-9]{1,15}_[0-9]{1,2}$').hasMatch(next!)) {
      throw const AppFailure('protocol', 'Invalid app page. Refresh the list.');
    }
  }
  final int revision;
  final String? next;
  final List<TvApp> apps;
}

abstract interface class AppsApi {
  Future<AppsPage> page(PairCredential credential, String? cursor);
  Future<Uint8List> icon(PairCredential credential, String package);
  Future<void> launch(
    PairCredential credential,
    String package,
    int revision,
    String id,
  );
  void cancel();
}

class PinnedAppsApi implements AppsApi {
  final _clients = <HttpClient>{};
  Future<Uint8List> request(
    PairCredential c,
    String path, {
    String? cursor,
    Map<String, Object>? body,
    bool image = false,
    String? method,
    String? ifMatch,
  }) async {
    final client = PinnedTvApi.clientFor(c.endpoint, c.pinHash);
    _clients.add(client);
    try {
      return await (() async {
        final uri = c.endpoint
            .route(path)
            .replace(
              queryParameters: cursor == null ? null : {'cursor': cursor},
            );
        final request = await client.openUrl(
          method ?? (body == null ? 'GET' : 'POST'),
          uri,
        );
        request.followRedirects = false;
        request.persistentConnection = false;
        request.headers.set(
          HttpHeaders.authorizationHeader,
          'Bearer ${c.token}',
        );
        if (ifMatch != null) {
          request.headers.set(HttpHeaders.ifMatchHeader, ifMatch);
        }
        if (body != null) {
          final bytes = utf8.encode(jsonEncode(body));
          if (bytes.length > 8192) {
            throw const AppFailure('input', 'The changes are too large.');
          }
          request.headers.set(
            HttpHeaders.contentTypeHeader,
            'application/json',
          );
          request.contentLength = bytes.length;
          request.add(bytes);
        }
        final response = await request.close();
        final bytes = BytesBuilder(copy: false);
        await for (final chunk in response) {
          if (bytes.length + chunk.length > (image ? 8192 : 16384)) {
            throw const AppFailure(
              'protocol',
              'TV response exceeded its limit.',
            );
          }
          bytes.add(chunk);
        }
        if (response.statusCode != 200) {
          throw switch (response.statusCode) {
            401 => const AppFailure(
              'unauthorized',
              'Pairing was revoked. Go back, forget this connection and pair again.',
            ),
            404 => const AppFailure(
              'not_found',
              'This app is unavailable. Refresh the app list.',
            ),
            503 when ifMatch != null => const AppFailure(
              'unavailable',
              'The TV could not save these changes. Your draft is kept; reload before retrying.',
            ),
            400 => const AppFailure(
              'input',
              'The TV rejected these changes. Check limits and refresh the installed apps.',
            ),
            409 when ifMatch != null => const AppFailure(
              'conflict',
              'Another phone changed the TV configuration. Your draft is kept. Reload before saving again.',
            ),
            409 => const AppFailure(
              'unavailable',
              'Open Family TV on the TV and refresh the app list before retrying.',
            ),
            _ => const AppFailure(
              'unavailable',
              'App catalog unavailable. Open Family TV and retry.',
            ),
          };
        }
        if (image && response.headers.contentType?.mimeType != 'image/png') {
          throw const AppFailure('protocol', 'Invalid app image.');
        }
        return bytes.takeBytes();
      })().timeout(const Duration(seconds: 8));
    } catch (error) {
      throw networkFailure(error);
    } finally {
      _clients.remove(client);
      client.close(force: true);
    }
  }

  @override
  Future<AppsPage> page(PairCredential c, String? cursor) async {
    try {
      return AppsPage.fromJson(
        jsonDecode(utf8.decode(await request(c, '/v1/apps', cursor: cursor)))
            as Map<String, dynamic>,
      );
    } catch (error) {
      if (error is AppFailure) rethrow;
      throw const AppFailure(
        'protocol',
        'Invalid catalog. Check the launcher version.',
      );
    }
  }

  @override
  Future<Uint8List> icon(PairCredential c, String package) =>
      request(c, '/v1/apps/$package/icon', image: true);
  @override
  Future<void> launch(
    PairCredential c,
    String package,
    int revision,
    String id,
  ) async {
    final data = jsonDecode(
      utf8.decode(
        await request(
          c,
          '/v1/apps/$package/launch',
          body: {'id': id, 'revision': revision},
        ),
      ),
    );
    if (data is! Map ||
        data['apiVersion'] != 1 ||
        data['id'] != id ||
        data['accepted'] != true) {
      throw const AppFailure(
        'protocol',
        'Launch was not acknowledged. Check the TV before retrying.',
      );
    }
  }

  @override
  void cancel() {
    for (final client in _clients) {
      client.close(force: true);
    }
    _clients.clear();
  }
}
