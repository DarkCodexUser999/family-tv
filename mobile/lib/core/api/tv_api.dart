import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

import 'models.dart';

abstract interface class TvApi {
  Future<TvInfo> info(TvEndpoint endpoint, String certificate);
  Future<PairCredential> pair(
    TvEndpoint endpoint,
    String certificate,
    TvInfo info,
    String pin,
    String name,
  );
}

class PinnedTvApi implements TvApi {
  static bool matchesCertificate(X509Certificate cert, String expected) =>
      sha256.convert(cert.der).toString() == expected &&
      DateTime.now().isAfter(cert.startValidity) &&
      DateTime.now().isBefore(cert.endValidity);

  Future<Map<String, dynamic>> _request(
    TvEndpoint target,
    String certificate,
    String path, {
    Map<String, String>? body,
  }) async {
    final expected = fingerprint(certificate);
    // Empty roots ensure every certificate must pass our exact TV pin, even a public CA certificate.
    final client = HttpClient(context: SecurityContext(withTrustedRoots: false))
      ..connectionTimeout = const Duration(seconds: 4)
      ..findProxy = ((_) => 'DIRECT')
      ..badCertificateCallback = (cert, host, port) =>
          host == target.address &&
          port == target.port &&
          matchesCertificate(cert, expected);
    try {
      return await (() async {
        final request = await client.openUrl(
          body == null ? 'GET' : 'POST',
          target.route(path),
        );
        request.followRedirects = false;
        request.persistentConnection = false;
        if (body != null) {
          final bytes = utf8.encode(jsonEncode(body));
          if (bytes.length > 1024) {
            throw const AppFailure('input', 'Pairing information is too long.');
          }
          request.headers.set(
            HttpHeaders.contentTypeHeader,
            'application/json',
          );
          request.contentLength = bytes.length;
          request.add(bytes);
        }
        final response = await request.close();
        final bytes = <int>[];
        await for (final chunk in response) {
          if (bytes.length + chunk.length > 16384) {
            throw const AppFailure(
              'protocol',
              'The TV response was too large.',
            );
          }
          bytes.addAll(chunk);
        }
        if (response.statusCode != 200) {
          final failure = switch (response.statusCode) {
            401 => const AppFailure(
              'unauthorized',
              'Pairing was refused. Open a fresh pairing window on the TV and check its PIN.',
            ),
            429 => const AppFailure(
              'rate_limited',
              'Too many attempts. Wait two minutes before trying again.',
            ),
            503 => const AppFailure(
              'busy',
              'The TV cannot pair now. Check its phone limit or try again.',
            ),
            _ => const AppFailure(
              'protocol',
              'The TV could not complete this request. Check its launcher version.',
            ),
          };
          throw failure;
        }
        final value = jsonDecode(utf8.decode(bytes));
        if (value is! Map<String, dynamic>) throw const FormatException();
        return value;
      })().timeout(const Duration(seconds: 8));
    } catch (error) {
      throw networkFailure(error);
    } finally {
      client.close(force: true);
    }
  }

  @override
  Future<TvInfo> info(TvEndpoint endpoint, String certificate) async {
    try {
      return TvInfo.fromJson(await _request(endpoint, certificate, '/v1/info'));
    } catch (error) {
      throw error is AppFailure
          ? error
          : const AppFailure('protocol', 'Invalid TV information.');
    }
  }

  @override
  Future<PairCredential> pair(
    TvEndpoint endpoint,
    String certificate,
    TvInfo info,
    String pin,
    String name,
  ) async {
    if (!info.pairingOpen ||
        info.sessionId == null ||
        !RegExp(r'^[0-9]{6}$').hasMatch(pin) ||
        name.trim().isEmpty ||
        name.length > 48) {
      throw const AppFailure(
        'input',
        'Open pairing on the TV, enter its six-digit PIN, and provide a phone name (up to 48 characters).',
      );
    }
    final json = await _request(
      endpoint,
      certificate,
      '/v1/pair',
      body: {
        'sessionId': info.sessionId!,
        'pin': pin,
        'deviceName': name.trim(),
      },
    );
    try {
      if (json['apiVersion'] != 1) throw const FormatException();
      return PairCredential(
        endpoint: endpoint,
        pinHash: fingerprint(certificate),
        installationId: info.installationId,
        deviceId: json['deviceId'] as String,
        token: json['token'] as String,
      );
    } catch (_) {
      throw const AppFailure(
        'protocol',
        'The TV returned invalid credentials. Revoke phone access on the TV before retrying.',
      );
    }
  }
}
