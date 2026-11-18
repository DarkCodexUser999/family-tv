import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../api/models.dart';

abstract interface class CredentialStore {
  Future<PairCredential?> load();
  Future<void> save(PairCredential value);
  Future<void> forget();
}

class SecureCredentialStore implements CredentialStore {
  static const _key = 'family-tv-pairing-v1';
  final FlutterSecureStorage _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
      synchronizable: false,
    ),
  );
  @override
  Future<PairCredential?> load() async {
    try {
      final value = await _storage.read(key: _key);
      return value == null
          ? null
          : PairCredential.fromJson(jsonDecode(value) as Map<String, dynamic>);
    } catch (_) {
      throw const AppFailure(
        'storage',
        'Saved pairing could not be read. Unlock your phone and retry, or forget this saved connection.',
      );
    }
  }

  @override
  Future<void> save(PairCredential value) async {
    try {
      final encoded = jsonEncode(value.toJson());
      await _storage.write(key: _key, value: encoded);
      if (await _storage.read(key: _key) != encoded) {
        throw const AppFailure('storage', 'Credential verification failed');
      }
    } catch (_) {
      throw const AppFailure(
        'storage',
        'Pairing succeeded on TV, but this phone could not save it. Revoke phone access on TV before pairing again.',
      );
    }
  }

  @override
  Future<void> forget() async {
    try {
      await _storage.delete(key: _key);
    } catch (_) {
      throw const AppFailure(
        'storage',
        'Could not remove saved pairing. Unlock your phone and retry.',
      );
    }
  }
}
