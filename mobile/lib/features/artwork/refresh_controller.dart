import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;

import '../../core/api/models.dart';
import '../../core/api/tv_api.dart';
import '../../core/api/tmdb_client.dart';
import 'tmdb_settings.dart';
import 'artwork_api.dart';

class ProviderApi {
  ProviderApi(this.credential);
  final PairCredential credential;
  HttpClient? _client;
  Future<Map<String, dynamic>> post(String path, {String? job}) async {
    final client = PinnedTvApi.clientFor(
      credential.endpoint,
      credential.pinHash,
    );
    _client = client;
    try {
      return await (() async {
        final request = await client.postUrl(credential.endpoint.route(path));
        request.followRedirects = false;
        request.headers.set('Authorization', 'Bearer ${credential.token}');
        if (job != null) request.headers.set('X-Job-Id', job);
        request.contentLength = 0;
        final response = await request.close();
        final text = StringBuffer();
        await for (final part in response.transform(utf8.decoder)) {
          if (text.length + part.length > 4096) {
            throw const AppFailure('protocol', 'Invalid TV response.');
          }
          text.write(part);
        }
        if (response.statusCode != 200) {
          throw const AppFailure(
            'refresh',
            'TV refresh unavailable or another phone is working.',
          );
        }
        final result = jsonDecode(text.toString());
        if (result is! Map<String, dynamic>) {
          throw const AppFailure('protocol', 'Invalid TV response.');
        }
        return result;
      })().timeout(const Duration(seconds: 8));
    } on AppFailure {
      rethrow;
    } catch (_) {
      throw const AppFailure(
        'network',
        'TV connection interrupted. Check refresh status before retrying.',
      );
    } finally {
      client.close(force: true);
      if (identical(_client, client)) _client = null;
    }
  }

  void cancel() {
    _client?.close(force: true);
  }
}

/// Foreground-only and single-flight. A missing key leaves an explicit phone-required job.
class RefreshController {
  RefreshController(this.provider, this.artwork, this.tmdb, this.settings);
  final ProviderApi provider;
  final ArtworkApi artwork;
  final TmdbClient tmdb;
  final TmdbSettings settings;
  bool busy = false, _canceled = false;
  Future<void> run(
    String pkg,
    TmdbChoice choice, {
    void Function(String)? state,
  }) async {
    if (busy) throw const AppFailure('busy', 'A refresh is already running.');
    busy = true;
    _canceled = false;
    String? job;
    bool claimed = false;
    void check() {
      if (_canceled) {
        throw const AppFailure(
          'canceled',
          'Refresh canceled. Check the TV before retrying.',
        );
      }
    }

    try {
      check();
      final result = await provider.post('/v1/provider/refresh/$pkg');
      job = result['jobId'] as String?;
      if (job == null || !RegExp(r'^[a-f0-9-]{36}$').hasMatch(job)) {
        throw const AppFailure('protocol', 'Invalid refresh receipt.');
      }
      check();
      final token = await settings.load();
      check();
      if (token == null) {
        throw const AppFailure(
          'phone_required',
          'This phone needs TMDB setup; refresh is pending.',
        );
      }
      await provider.post('/v1/provider/refresh/$pkg/claim', job: job);
      claimed = true;
      check();
      state?.call('Downloading artwork…');
      final bytes = await tmdb.image(token, choice);
      check();
      final buffer = await ui.ImmutableBuffer.fromUint8List(bytes);
      ui.ImageDescriptor? descriptor;
      try {
        descriptor = await ui.ImageDescriptor.encoded(buffer);
        if (descriptor.width < 1 ||
            descriptor.height < 1 ||
            descriptor.width * descriptor.height > 8000000) {
          throw const AppFailure('size', 'Artwork exceeds 8 megapixels.');
        }
      } finally {
        descriptor?.dispose();
        buffer.dispose();
      }
      check();
      state?.call('Uploading artwork…');
      await artwork.upload(pkg, 'backdrop', bytes, 500, 500);
      check();
      await provider.post('/v1/provider/refresh/$pkg/complete', job: job);
      state?.call('Backdrop refreshed.');
    } catch (_) {
      if (claimed && job != null) {
        try {
          await provider.post(
            '/v1/provider/refresh/$pkg/${_canceled ? 'canceled' : 'error'}',
            job: job,
          );
        } catch (_) {
          /* Lease expires without polling. */
        }
      }
      rethrow;
    } finally {
      busy = false;
    }
  }

  void cancel() {
    _canceled = true;
    provider.cancel();
    tmdb.cancel();
    artwork.cancel();
  }
}
