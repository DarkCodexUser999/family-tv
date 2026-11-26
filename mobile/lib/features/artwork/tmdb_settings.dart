import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../core/api/models.dart';

class TmdbSettings {
  static const key = 'family-tv-tmdb-read-token-v1';
  final FlutterSecureStorage storage;
  const TmdbSettings({
    this.storage = const FlutterSecureStorage(
      aOptions: AndroidOptions(),
      iOptions: IOSOptions(
        accessibility: KeychainAccessibility.first_unlock_this_device,
        synchronizable: false,
      ),
    ),
  });
  Future<String?> load() async {
    try {
      return await storage.read(key: key);
    } catch (_) {
      throw const AppFailure(
        'storage',
        'Unlock your phone to read TMDB setup.',
      );
    }
  }

  Future<void> save(String value) async {
    if (!RegExp(r'^[A-Za-z0-9_.-]{20,2048}$').hasMatch(value)) {
      throw const AppFailure('key', 'Paste your TMDB API Read Access Token.');
    }
    try {
      await storage.write(key: key, value: value);
      if (await storage.read(key: key) != value) {
        throw StateError('verification');
      }
    } catch (_) {
      throw const AppFailure('storage', 'Could not securely save TMDB setup.');
    }
  }

  Future<void> remove() async {
    try {
      await storage.delete(key: key);
    } catch (_) {
      throw const AppFailure('storage', 'Could not remove TMDB setup.');
    }
  }
}
