import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import '../../core/api/models.dart';
import '../../core/api/tv_api.dart';

class ArtworkApi {
  final PairCredential credential;
  ArtworkApi(this.credential);
  HttpClient? _client;
  bool _canceled = false;
  Future<Map<String, dynamic>> _request(
    String method,
    String path, {
    Uint8List? bytes,
    String? id,
    int x = 500,
    int y = 500,
    void Function(double)? progress,
  }) async {
    final client = PinnedTvApi.clientFor(
      credential.endpoint,
      credential.pinHash,
    );
    _client = client;
    try {
      return await (() async {
        final request = await client.openUrl(
          method,
          credential.endpoint.route(path),
        );
        request.followRedirects = false;
        request.persistentConnection = false;
        request.headers.set('Authorization', 'Bearer ${credential.token}');
        if (id != null) request.headers.set('X-Upload-Id', id);
        if (bytes != null) {
          if (bytes.length < 8 || bytes.length > 2097152) {
            throw const AppFailure(
              'image',
              'Choose a PNG or JPEG up to 2 MiB.',
            );
          }
          request.headers.set(
            'Content-Type',
            bytes[0] == 137 ? 'image/png' : 'image/jpeg',
          );
          request.headers.set('X-Crop-X', '$x');
          request.headers.set('X-Crop-Y', '$y');
          request.contentLength = bytes.length;
          for (int offset = 0; offset < bytes.length; offset += 16384) {
            if (_canceled) {
              throw const AppFailure('canceled', 'Upload canceled.');
            }
            final end = (offset + 16384).clamp(0, bytes.length);
            request.add(bytes.sublist(offset, end));
            await request.flush();
            progress?.call(end / bytes.length);
          }
        } else {
          request.contentLength = 0;
        }
        final response = await request.close();
        final data = BytesBuilder();
        await for (final chunk in response) {
          if (data.length + chunk.length > 4096) {
            throw const AppFailure('protocol', 'Invalid upload response.');
          }
          data.add(chunk);
        }
        if (response.statusCode != 200) {
          throw AppFailure(
            'upload',
            response.statusCode == 401 ? 'Pairing was refused. Pair again.' : 'Upload was refused. Check format, size, TV storage and connection.',
          );
        }
        final value = jsonDecode(utf8.decode(data.takeBytes()));
        if (value is! Map<String, dynamic> || value['apiVersion'] != 1) {
          throw const AppFailure('protocol', 'Invalid upload response.');
        }
        return value;
      })().timeout(const Duration(seconds: 30));
    } catch (e) {
      if (_canceled) {
        throw const AppFailure(
          'canceled',
          'Connection canceled. Check TV artwork before retrying.',
        );
      }
      throw networkFailure(e);
    } finally {
      client.close(force: true);
      if (identical(_client, client)) _client = null;
    }
  }

  Future<void> upload(
    String pkg,
    String kind,
    Uint8List bytes,
    int x,
    int y, {
    void Function(double)? progress,
    void Function()? committing,
  }) async {
    _canceled = false;
    final path = '/v1/artwork/$pkg/$kind';
    final staged = await _request(
      'PUT',
      path,
      bytes: bytes,
      x: x,
      y: y,
      progress: progress,
    );
    final id = staged['uploadId'];
    if (id is! String || !RegExp(r'^[0-9a-f]{32}$').hasMatch(id)) {
      throw const AppFailure('protocol', 'Invalid upload receipt.');
    }
    if (_canceled) throw const AppFailure('canceled', 'Upload canceled.');
    committing?.call();
    final done = await _request('POST', '$path/commit', id: id);
    if (done['accepted'] != true) {
      throw const AppFailure(
        'protocol',
        'Upload confirmation uncertain. Check the TV before retrying.',
      );
    }
  }

  Future<void> reset(String pkg, String kind) async {
    _canceled = false;
    final result = await _request('DELETE', '/v1/artwork/$pkg/$kind');
    if (result['accepted'] != true) {
      throw const AppFailure('protocol', 'Reset not confirmed.');
    }
  }

  void cancel() {
    _canceled = true;
    _client?.close(force: true);
  }
}
