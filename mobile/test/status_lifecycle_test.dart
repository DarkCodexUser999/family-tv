import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/features/status/status_api.dart';
import 'package:family_tv_remote/features/status/status_controller.dart';
import 'package:family_tv_remote/features/status/status_screen.dart';

import 'status_test.dart' as s;
import 'widget_test.dart' as f;

class SlowStatus extends StatusApi {
  final requests = <Completer<TvStatus>>[];
  int canceled = 0;
  @override
  Future<TvStatus> fetch(PairCredential credential) {
    final result = Completer<TvStatus>();
    requests.add(result);
    return result.future;
  }

  @override
  void cancel() {
    canceled++;
  }
}

void main() {
  test(
    'slow requests coalesce; background reply cannot replace resumed state',
    () async {
      final api = SlowStatus();
      final controller = StatusController(api, f.credential());
      final first = controller.refresh();
      await controller.refresh();
      expect(api.requests.length, 1);
      controller.suspend();
      expect(api.canceled, 1);
      expect(controller.value, null);
      controller.resume();
      expect(api.requests.length, 2);
      api.requests[1].complete(TvStatus.parse(s.snapshot(job: 'complete')));
      await Future<void>.delayed(Duration.zero);
      api.requests[0].complete(TvStatus.parse(s.snapshot(job: 'error')));
      await first;
      expect(controller.value!.job, 'complete');
      expect(controller.busy, false);
      controller.dispose();
    },
  );
  test(
    'revoked access clears stale ready state; disposed response is ignored',
    () async {
      final api = s.FakeStatus();
      final c = StatusController(api, f.credential());
      await c.refresh();
      expect(c.value!.canNavigate, true);
      api.error = const AppFailure('unauthorized', 'Pair again');
      await c.refresh();
      expect(c.value, null);
      expect(c.error!.code, 'unauthorized');
      c.dispose();
      final slow = SlowStatus();
      final disposed = StatusController(slow, f.credential()),
          pending = disposed.refresh();
      disposed.dispose();
      slow.requests.single.complete(TvStatus.parse(s.snapshot()));
      await pending;
      expect(disposed.value, null);
    },
  );
  testWidgets(
    '200 percent text fits small phone and explains unavailable capabilities',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final data = s.snapshot(
        foreground: false,
        ready: false,
        job: 'phone_required',
      );
      data['capabilities'] = {for (final key in TvStatus.features) key: false};
      final api = s.FakeStatus()..value = TvStatus.parse(data);
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: TextScaler.linear(2)),
            child: child!,
          ),
          home: StatusScreen(controller: StatusController(api, f.credential())),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), null);
      await tester.scrollUntilVisible(find.textContaining('Waiting for a phone'), 150);
      expect(find.textContaining('Waiting for a phone'), findsOneWidget);
      await tester.scrollUntilVisible(find.textContaining('Family TV navigation: unavailable'), 150);
      expect(find.textContaining('Family TV navigation: unavailable'), findsOneWidget);
      await tester.scrollUntilVisible(find.text('Refresh status'), 200);
      expect(tester.takeException(), null);
      expect(find.text('Refresh status'), findsOneWidget);
    },
  );
}
