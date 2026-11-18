import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:family_tv_remote/app.dart';

void main() {
  testWidgets('app starts without opening network connections', (tester) async {
    var connections = 0;
    await HttpOverrides.runZoned(() async {
      await tester.pumpWidget(const FamilyTvApp());
      await tester.pumpAndSettle();
    }, createHttpClient: (_) { connections++; throw StateError('Unexpected startup network'); });
    expect(connections, 0);
    expect(find.text('Secure home Wi-Fi pairing'), findsOneWidget);
  });
}
