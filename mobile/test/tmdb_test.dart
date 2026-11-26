import 'package:flutter_test/flutter_test.dart';
import 'package:family_tv_remote/core/api/tmdb_client.dart';
import 'package:family_tv_remote/core/api/models.dart';

void main() {
  test('bounded choices exclude adult, missing and unsafe CDN paths', () {
    final result = TmdbClient.choices({
      'results': [
        {'id': 1, 'title': 'Movie', 'backdrop_path': '/safe.jpg'},
        {
          'id': 2,
          'title': 'Adult',
          'adult': true,
          'backdrop_path': '/safe.jpg',
        },
        {'id': 3, 'title': 'Bad', 'backdrop_path': '//elsewhere/secret.jpg'},
        {'id': 4, 'title': 'None', 'backdrop_path': null},
      ],
    });
    expect(result.map((v) => v.title), ['Movie']);
    expect(
      TmdbClient.choices({
        'results': List.generate(
          30,
          (i) => {'id': i + 1, 'title': 'M', 'backdrop_path': '/safe.jpg'},
        ),
      }).length,
      20,
    );
  });
  test('malformed response fails safely', () {
    expect(
      () => TmdbClient.choices({'results': 'bad'}),
      throwsA(isA<AppFailure>()),
    );
  });
}
