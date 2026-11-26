import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'models.dart';

class TmdbChoice {
  const TmdbChoice(this.title, this.path);
  final String title, path;
}

/// System-trusted HTTPS only; never sends the bearer to the image CDN.
class TmdbClient {
  HttpClient? _active;
  DateTime? retryAfter;
  bool _busy = false;
  final HttpClient Function() createClient;
  TmdbClient({HttpClient Function()? createClient})
    : createClient = createClient ?? HttpClient.new;
  static List<TmdbChoice> choices(Object? data) {
    if (data is! Map || data['results'] is! List) {
      throw const AppFailure('protocol', 'Invalid TMDB response.');
    }
    return (data['results'] as List)
        .take(20)
        .whereType<Map>()
        .where(
          (v) =>
              v['adult'] != true &&
              v['id'] is int &&
              v['id'] > 0 &&
              v['title'] is String &&
              (v['title'] as String).length <= 200 &&
              v['backdrop_path'] is String &&
              RegExp(r'^/[A-Za-z0-9]{1,150}\.(jpg|png)$')
                  .hasMatch(v['backdrop_path']),
        )
        .map((v) => TmdbChoice(v['title'], v['backdrop_path']))
        .toList();
  }

  Future<Uint8List> _get(Uri uri, String? token, int limit) async {
    if (_busy) {
      throw const AppFailure('busy', 'A TMDB request is already running.');
    }
    if (retryAfter != null && DateTime.now().isBefore(retryAfter!)) {
      throw const AppFailure(
        'rate_limit',
        'TMDB is busy. Wait a minute before retrying.',
      );
    }
    _busy = true;
    final client = createClient()..findProxy = (_) => 'DIRECT';
    _active = client;
    try {
      return await (() async {
        final request = await client.getUrl(uri);
        request.followRedirects = false;
        if (token != null) {
          request.headers.set('Authorization', 'Bearer $token');
        }
        final response = await request.close();
        if (response.statusCode == 401) {
          throw const AppFailure(
            'key',
            'TMDB rejected this token. Update setup.',
          );
        }
        if (response.statusCode == 429) {
          retryAfter = DateTime.now().add(const Duration(seconds: 60));
          throw const AppFailure(
            'rate_limit',
            'TMDB is busy. Wait a minute before retrying.',
          );
        }
        if (response.statusCode != 200 || response.contentLength > limit) {
          throw const AppFailure(
            'provider',
            'TMDB response unavailable or too large.',
          );
        }
        final result = BytesBuilder(copy: false);
        await for (final chunk in response) {
          if (result.length + chunk.length > limit) {
            throw const AppFailure('size', 'TMDB response too large.');
          }
          result.add(chunk);
        }
        return result.takeBytes();
      })().timeout(const Duration(seconds: 15));
    } on AppFailure {
      rethrow;
    } catch (_) {
      throw const AppFailure(
        'network',
        'TMDB unavailable. Check internet or retry later.',
      );
    } finally {
      client.close(force: true);
      _active = null;
      _busy = false;
    }
  }

  Future<List<TmdbChoice>> search(String token, String query) async {
    if (query.trim().isEmpty || query.length > 100) {
      throw const AppFailure(
        'query',
        'Enter a movie title up to 100 characters.',
      );
    }
    final bytes = await _get(
      Uri.https('api.themoviedb.org', '/3/search/movie', {
        'query': query.trim(),
        'include_adult': 'false',
        'page': '1',
        'language': 'en-US',
      }),
      token,
      262144,
    );
    try {
      return choices(jsonDecode(utf8.decode(bytes)));
    } on AppFailure {
      rethrow;
    } catch (_) {
      throw const AppFailure('protocol', 'Invalid TMDB response.');
    }
  }

  Future<Uint8List> image(String token, TmdbChoice choice) async {
    if (!RegExp(r'^/[A-Za-z0-9]{1,150}\.(jpg|png)$').hasMatch(choice.path)) {
      throw const AppFailure('image', 'Invalid artwork path.');
    }
    final config = jsonDecode(
      utf8.decode(
        await _get(
          Uri.https('api.themoviedb.org', '/3/configuration'),
          token,
          262144,
        ),
      ),
    );
    if (config is! Map ||
        config['images'] is! Map ||
        config['images']['secure_base_url'] != 'https://image.tmdb.org/t/p/' ||
        config['images']['backdrop_sizes'] is! List ||
        !(config['images']['backdrop_sizes'] as List).contains('w780')) {
      throw const AppFailure(
        'provider',
        'TMDB image configuration unavailable.',
      );
    }
    return _get(
      Uri.https('image.tmdb.org', '/t/p/w780${choice.path}'),
      null,
      2097152,
    );
  }

  void cancel() {
    _active?.close(force: true);
  }
}
