"""iOS source configuration only; this is not a native build or device test."""
import plistlib
from pathlib import Path
r = Path(__file__).resolve().parents[1]
info = plistlib.loads((r/'mobile/ios/Runner/Info.plist').read_bytes())
assert info['NSBonjourServices'] == ['_familytv._tcp']
assert info['NSLocalNetworkUsageDescription'].strip()
ats = info.get('NSAppTransportSecurity', {})
assert not ats.get('NSAllowsArbitraryLoads', False)
assert not ats.get('NSAllowsArbitraryLoadsInWebContent', False)
for name in ['DebugProfile', 'Release']:
    entitlements = plistlib.loads((r/f'mobile/ios/Runner/{name}.entitlements').read_bytes())
    assert entitlements['keychain-access-groups'] == []
project = (r/'mobile/ios/Runner.xcodeproj/project.pbxproj').read_text()
assert 'Runner/DebugProfile.entitlements' in project and 'Runner/Release.entitlements' in project
assert 'IPHONEOS_DEPLOYMENT_TARGET = 15.0' in project
assert "platform :ios, '15.0'" in (r/'mobile/ios/Podfile').read_text()
print('PASS: iOS plist/Bonjour/ATS/entitlements/deployment source guards (native build UNVERIFIED)')
