import 'dart:io';

class AppFailure implements Exception {
  const AppFailure(this.code, this.message);
  final String code;
  final String message;
  @override
  String toString() => 'FamilyTV error: $code'; // Never include responses or secrets.
}

class TvEndpoint {
  const TvEndpoint._(this.address, this.port);
  final String address;
  final int port;
  static bool privateAddress(String address) {
    final parts = address.split('.');
    if (parts.length != 4) return false;
    final numbers = <int>[];
    for (final part in parts) {
      final n = int.tryParse(part);
      if (n == null || n < 0 || n > 255 || n.toString() != part) return false;
      numbers.add(n);
    }
    return numbers[0] == 10 ||
        numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31 ||
        numbers[0] == 192 && numbers[1] == 168;
  }

  factory TvEndpoint.parse(String value) {
    final input = value.trim();
    final uri = Uri.tryParse(input.contains('://') ? input : 'https://$input');
    if (uri == null ||
        uri.scheme != 'https' ||
        !privateAddress(uri.host) ||
        !uri.hasPort ||
        uri.port < 1 ||
        uri.port > 65535 ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment ||
        !{'', '/', '/v1/info'}.contains(uri.path)) {
      throw const AppFailure(
        'address',
        'Enter the private Wi-Fi IP and port shown on your TV, such as 192.168.1.20:48322.',
      );
    }
    return TvEndpoint._(uri.host, uri.port);
  }
  Uri route(String path) =>
      Uri(scheme: 'https', host: address, port: port, path: path);
  @override
  String toString() => '$address:$port';
}

String fingerprint(String input) {
  final value = input.replaceAll(RegExp(r'[\s:]'), '').toLowerCase();
  if (!RegExp(r'^[0-9a-f]{64}$').hasMatch(value)) {
    throw const AppFailure(
      'fingerprint',
      'Copy all 64 fingerprint characters from TV connection details.',
    );
  }
  return value;
}

class TvInfo {
  TvInfo.fromJson(Map<String, dynamic> json)
    : installationId = json['installationId'] as String? ?? '',
      pairingOpen = json['pairingOpen'] == true,
      sessionId = json['pairingSessionId'] as String? {
    if (json['apiVersion'] != 1 ||
        !RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(installationId) ||
        json['pairingOpen'] is! bool ||
        pairingOpen &&
            (sessionId == null ||
                !RegExp(r'^[0-9a-f]{32}$').hasMatch(sessionId!))) {
      throw const AppFailure(
        'protocol',
        'The TV response is not compatible with this app.',
      );
    }
  }
  final String installationId;
  final bool pairingOpen;
  final String? sessionId;
}

class PairCredential {
  PairCredential({
    required this.endpoint,
    required this.pinHash,
    required this.installationId,
    required this.deviceId,
    required this.token,
  }) {
    if (!RegExp(r'^[0-9a-f]{64}$').hasMatch(token) ||
        !RegExp(r'^[0-9a-f]{32}$').hasMatch(deviceId) ||
        !RegExp(r'^[A-Za-z0-9_-]{1,64}$').hasMatch(installationId)) {
      throw const AppFailure(
        'protocol',
        'The TV returned invalid pairing credentials.',
      );
    }
    fingerprint(pinHash);
  }
  final TvEndpoint endpoint;
  final String pinHash; // Certificate fingerprint, never the six-digit PIN.
  final String installationId;
  final String deviceId;
  final String token;
  Map<String, String> toJson() => {
    'endpoint': endpoint.toString(),
    'fingerprint': pinHash,
    'installationId': installationId,
    'deviceId': deviceId,
    'token': token,
  };
  factory PairCredential.fromJson(Map<String, dynamic> value) => PairCredential(
    endpoint: TvEndpoint.parse(value['endpoint'] as String),
    pinHash: fingerprint(value['fingerprint'] as String),
    installationId: value['installationId'] as String,
    deviceId: value['deviceId'] as String,
    token: value['token'] as String,
  );
  @override
  String toString() => 'PairCredential(redacted)';
}

AppFailure networkFailure(Object error) {
  if (error is AppFailure) return error;
  if (error is HandshakeException) {
    return const AppFailure(
      'certificate',
      'The certificate did not match. Check the fingerprint on your TV; do not bypass this check.',
    );
  }
  return const AppFailure(
    'network',
    'Cannot reach Family TV. Check home Wi-Fi, the TV address and Remote access, then retry.',
  );
}
