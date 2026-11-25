package app.familytv.family_tv_remote
import android.app.Activity
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
class MainActivity : FlutterActivity() {
 private var pending: MethodChannel.Result? = null
 override fun configureFlutterEngine(engine: FlutterEngine) {
  super.configureFlutterEngine(engine)
  MethodChannel(engine.dartExecutor.binaryMessenger,"familytv/artwork-picker").setMethodCallHandler { call, result ->
   if(call.method != "pick") { result.notImplemented() }
   else if(pending != null) { result.error("busy","Picker already open",null) }
   else { pending=result
    try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*").putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/png","image/jpeg")),411) }
    catch(e: Exception) { pending=null;result.error("picker","No image picker available",null) }
   }
  }
 }
 override fun onActivityResult(request: Int, code: Int, data: Intent?) {
  super.onActivityResult(request,code,data)
  if(request!=411)return
  val result=pending ?: return;pending=null
  if(code!=Activity.RESULT_OK||data?.data==null){result.success(null);return}
  val uri=data.data!!
  Thread {
   try {
    val bytes=ByteArrayOutputStream()
    contentResolver.openInputStream(uri).use { input ->
     requireNotNull(input);val buffer=ByteArray(8192)
     while(true){val n=input.read(buffer);if(n<0)break;if(bytes.size()+n>2097152)throw IllegalArgumentException("Choose PNG or JPEG up to 2 MiB");bytes.write(buffer,0,n)}
    }
    runOnUiThread { result.success(bytes.toByteArray()) }
   }catch(e:Exception){runOnUiThread{result.error("image","Choose a PNG or JPEG up to 2 MiB",null)}}
  }.start()
 }
}
