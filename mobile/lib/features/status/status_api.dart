import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../../core/api/models.dart';
import '../../core/api/tv_api.dart';

class TvStatus {
  TvStatus(
    this.foreground,
    this.canNavigate,
    this.revision,
    this.job,
    this.capabilities,
  );
  final bool foreground, canNavigate;
  final int revision;
  final String job;
  final Map<String, bool> capabilities;
  static const jobs = [
    'idle',
    'phone_required',
    'running',
    'complete',
    'canceled',
    'error',
  ];
  static const features = [
    'navigate',
    'launch',
    'rows',
    'hidden',
    'artwork',
    'refresh',
  ];
  factory TvStatus.parse(Map<String, dynamic> value) {
    if (value['apiVersion'] != 1 ||
        value['enabled'] != true ||
        value['foreground'] is! bool ||
        value['canNavigate'] is! bool ||
        value['catalogRevision'] is! int ||
        value['catalogRevision'] < 0 ||
        value['tmdbConfigured'] is! bool ||
        (value['canNavigate'] == true && value['foreground'] != true)) {
      throw const AppFailure(
        'protocol',
        'Incompatible TV status. Check launcher version.',
      );
    }
    final capabilities = value['capabilities'];
    if (capabilities != null &&
        (capabilities is! Map ||
            features.any((k) => capabilities[k] is! bool))) {
      throw const AppFailure('protocol', 'Invalid TV capabilities.');
    }
    final job = value['refreshState'] ?? 'idle';
    if (!jobs.contains(job)) {
      throw const AppFailure('protocol', 'Invalid artwork job status.');
    }
    return TvStatus(
      value['foreground'],
      value['canNavigate'],
      value['catalogRevision'],
      job,
      {for (final feature in features) feature: capabilities?[feature] == true},
    );
  }
}

class StatusApi {
  HttpClient? _client;
  Future<TvStatus> fetch(PairCredential credential) async {
    final client = PinnedTvApi.clientFor(
      credential.endpoint,
      credential.pinHash,
    );
    _client = client;
    try {
      return await (() async {
        final request = await client.getUrl(
          credential.endpoint.route('/v1/status'),
        );
        request.followRedirects = false;
        request.headers.set('Authorization', 'Bearer ${credential.token}');
        final response = await request.close();
        if (response.statusCode == 401) {
          throw const AppFailure(
            'unauthorized',
            'Phone access was revoked. Pair again from the connection screen.',
          );
        }
        if (response.statusCode != 200) {
          throw const AppFailure(
            'unavailable',
            'Status unavailable. Check Remote access on the TV.',
          );
        }
        final bytes = <int>[];
        await for (final chunk in response) {
          if (bytes.length + chunk.length > 4096) {
            throw const AppFailure('protocol', 'TV status response too large.');
          }
          bytes.addAll(chunk);
        }
        final value = jsonDecode(utf8.decode(bytes));
        if (value is! Map<String, dynamic>) {
          throw const AppFailure('protocol', 'Invalid TV status.');
        }
        return TvStatus.parse(value);
      })().timeout(const Duration(seconds: 8));
    } on AppFailure {
      rethrow;
    } catch (_) {
      throw const AppFailure(
        'connection',
        'TV unreachable. Check home Wi-Fi and Remote access, then retry.',
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
