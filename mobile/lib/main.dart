import 'package:flutter/widgets.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'app.dart';
import 'core/api/tv_api.dart';
import 'core/discovery/tv_discovery.dart';
import 'core/storage/credential_store.dart';
import 'features/pairing/pairing_controller.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  LicenseRegistry.addLicense(() async* {
    yield LicenseEntryWithLineBreaks([
      'Tink Android',
    ], await rootBundle.loadString('assets/TINK-LICENSE.txt'));
  });
  runApp(
    FamilyTvApp(
      controller: PairingController(
        api: PinnedTvApi(),
        store: SecureCredentialStore(),
        discovery: NsdTvDiscovery(),
      ),
    ),
  );
}
