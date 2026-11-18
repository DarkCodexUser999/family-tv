import 'package:flutter/widgets.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  LicenseRegistry.addLicense(() async* {
    yield LicenseEntryWithLineBreaks([
      'Tink Android',
    ], await rootBundle.loadString('assets/TINK-LICENSE.txt'));
  });
  runApp(const FamilyTvApp());
}
