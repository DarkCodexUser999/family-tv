import 'dart:async';
import 'dart:typed_data';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:family_tv_remote/core/api/models.dart';
import 'package:family_tv_remote/core/api/tmdb_client.dart';
import 'package:family_tv_remote/features/artwork/refresh_controller.dart';
import 'package:family_tv_remote/features/artwork/tmdb_settings.dart';
import 'artwork_test.dart' as a;
import 'widget_test.dart' as f;
class Store extends TmdbSettings {
 String? token='test-only-read-token';
 @override Future<String?> load()async=>token;
}
class Provider extends ProviderApi {
 Provider():super(f.credential());
 final paths=<String>[];
 @override Future<Map<String,dynamic>> post(String path,{String? job})async{
  paths.add(path);
  return {'jobId':'12345678-1234-1234-1234-123456789abc','state':'phone_required'};
 }
}
class Images extends TmdbClient {
 Completer<Uint8List>? pending;
 @override Future<Uint8List> image(String token,TmdbChoice choice)async=>pending==null?base64Decode(a.png):pending!.future;
 @override void cancel(){pending?.completeError(const AppFailure('canceled','Canceled'));pending=null;}
}
void main(){
 TestWidgetsFlutterBinding.ensureInitialized();
 const choice=TmdbChoice('Movie','/safe.jpg');
 test('missing local key leaves phone-required job without claiming',()async{
  final p=Provider(),s=Store()..token=null,art=a.FakeArtwork();
  final c=RefreshController(p,art,Images(),s);
  await expectLater(c.run('tv.youtube',choice),throwsA(isA<AppFailure>().having((e)=>e.code,'code','phone_required')));
  expect(p.paths,['/v1/provider/refresh/tv.youtube']);expect(art.uploads,0);expect(c.busy,false);
 });
 test('one foreground worker uploads and completes once',()async{
  final p=Provider(),art=a.FakeArtwork();
  final c=RefreshController(p,art,Images(),Store());
  await c.run('tv.youtube',choice);
  expect(art.uploads,1);expect(p.paths.last,endsWith('/complete'));expect(c.busy,false);
 });
 test('duplicate rejected and cancellation retains previous artwork',()async{
  final p=Provider(),art=a.FakeArtwork(),images=Images()..pending=Completer<Uint8List>();
  final c=RefreshController(p,art,images,Store());
  final first=c.run('tv.youtube',choice);
  final failed=expectLater(first,throwsA(isA<AppFailure>()));
  await Future<void>.delayed(Duration.zero);
  await expectLater(c.run('tv.youtube',choice),throwsA(isA<AppFailure>()));
  c.cancel();await failed;
  expect(art.uploads,0);expect(p.paths.last,endsWith('/canceled'));expect(c.busy,false);
 });
}
