import 'dart:convert';

import 'apps_api.dart';
import 'models.dart';

class RowsDocument {
  RowsDocument(this.revision, List<List<String>> rows)
    : rows = List.unmodifiable(
        rows.map((row) => List<String>.unmodifiable(row)),
      ) {
    final seen = <String>{};
    if (revision < 0 ||
        revision > 999999999999999 ||
        rows.isEmpty ||
        rows.length > 4 ||
        rows.any(
          (row) =>
              row.length > 6 ||
              row.any(
                (pkg) =>
                    pkg.length > 160 ||
                    !RegExp(
                      r'^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$',
                    ).hasMatch(pkg) ||
                    !seen.add(pkg),
              ),
        )) {
      throw const AppFailure(
        'input',
        'Use one to four rows, up to six apps each, with no duplicates.',
      );
    }
  }
  factory RowsDocument.fromJson(Map<String, dynamic> json) {
    if (json['apiVersion'] != 1) {
      throw const AppFailure('protocol', 'Unsupported TV rows version.');
    }
    return RowsDocument(
      json['revision'] as int,
      (json['rows'] as List)
          .map((r) => (r['packages'] as List).cast<String>())
          .toList(),
    );
  }
  final int revision;
  final List<List<String>> rows;
  Map<String, Object> toJson() => {
    'apiVersion': 1,
    'revision': revision,
    'rows': rows.map((row) => {'packages': row}).toList(),
  };
}

abstract interface class RowsApi {
  Future<RowsDocument> load(PairCredential credential);
  Future<RowsDocument> save(PairCredential credential, RowsDocument draft);
  void cancel();
}

class PinnedRowsApi implements RowsApi {
  final _http = PinnedAppsApi();
  Future<RowsDocument> _request(PairCredential c, [RowsDocument? draft]) async {
    try {
      final bytes = await _http.request(
        c,
        '/v1/rows',
        method: draft == null ? 'GET' : 'PUT',
        ifMatch: draft == null ? null : '"${draft.revision}"',
        body: draft?.toJson(),
      );
      return RowsDocument.fromJson(
        jsonDecode(utf8.decode(bytes)) as Map<String, dynamic>,
      );
    } catch (error) {
      if (error is AppFailure) rethrow;
      throw const AppFailure(
        'protocol',
        'Invalid TV rows response. Check the TV before retrying.',
      );
    }
  }

  @override
  Future<RowsDocument> load(PairCredential c) => _request(c);
  @override
  Future<RowsDocument> save(PairCredential c, RowsDocument draft) =>
      _request(c, draft);
  @override
  void cancel() => _http.cancel();
}
