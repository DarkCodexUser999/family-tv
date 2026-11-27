import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/features/status/status_api.dart';
import 'package:family_tv_remote/features/status/status_controller.dart';
import 'package:family_tv_remote/features/status/status_screen.dart';

import 'widget_test.dart' as f;

Map<String, dynamic> snapshot({
  bool foreground = true,
  bool ready = true,
  String job = 'idle',
}) => {
  'apiVersion': 1,
  'enabled': true,
  'foreground': foreground,
  'canNavigate': ready,
  'catalogRevision': 42,
  'tmdbConfigured': false,
  'refreshState': job,
  'capabilities': {for (final k in TvStatus.features) k: true},
};

class FakeStatus extends StatusApi {
  TvStatus? value = TvStatus.parse(snapshot());
  AppFailure? error;
  int requests = 0;
  @override
  Future<TvStatus> fetch(PairCredential credential) async {
    requests++;
    if (error != null) throw error!;
    return value!;
  }

  @override
  void cancel() {}
}

void main() {
  test('legacy status conservative capabilities and strict fields', () {
    final old = snapshot()
      ..remove('capabilities')
      ..remove('refreshState');
    expect(TvStatus.parse(old).capabilities.values.every((v) => !v), true);
    for (final bad in [
      snapshot()..['foreground'] = 'yes',
      snapshot()..['refreshState'] = 'secret',
      snapshot(foreground: false),
    ]) {
      expect(() => TvStatus.parse(bad), throwsA(isA<AppFailure>()));
    }
  });
  testWidgets('connected snapshot, manual refresh and job error explanation', (
    tester,
  ) async {
    final api = FakeStatus()..value = TvStatus.parse(snapshot(job: 'error'));
    await tester.pumpWidget(
      MaterialApp(
        home: StatusScreen(controller: StatusController(api, f.credential())),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Connected to Family TV'), findsOneWidget);
    expect(find.textContaining('Refresh failed'), findsOneWidget);
    expect(api.requests, 1);
    await tester.tap(find.text('Refresh status'));
    await tester.pumpAndSettle();
    expect(api.requests, 2);
  });
  testWidgets('disconnected and unavailable controls explained', (
    tester,
  ) async {
    final api = FakeStatus()
      ..error = const AppFailure('connection', 'TV unreachable');
    await tester.pumpWidget(
      MaterialApp(
        home: StatusScreen(controller: StatusController(api, f.credential())),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('TV unreachable'), findsOneWidget);
    api.error = null;
    api.value = TvStatus.parse(snapshot(foreground: false, ready: false));
    await tester.tap(find.text('Refresh status'));
    await tester.pumpAndSettle();
    expect(
      find.text('Open Family TV on the TV to use the pad'),
      findsOneWidget,
    );
  });
}
