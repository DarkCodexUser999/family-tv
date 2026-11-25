import Flutter
import UIKit

class SceneDelegate: FlutterSceneDelegate {
  private var artworkPicker: ArtworkPicker?
  override func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
    super.scene(scene,willConnectTo:session,options:connectionOptions)
    if let controller=window?.rootViewController as? FlutterViewController {
      artworkPicker=ArtworkPicker(messenger:controller.binaryMessenger,presenter:controller)
    }
  }

}

import UniformTypeIdentifiers
final class ArtworkPicker: NSObject, UIDocumentPickerDelegate {
 private var pending: FlutterResult?
 private weak var presenter: UIViewController?
 init(messenger: FlutterBinaryMessenger, presenter: UIViewController) {
  super.init(); self.presenter=presenter
  let channel=FlutterMethodChannel(name:"familytv/artwork-picker",binaryMessenger:messenger)
  channel.setMethodCallHandler { [weak self] call,result in
   guard let self=self else {return}
   guard call.method=="pick" else {result(FlutterMethodNotImplemented);return}
   guard self.pending==nil else {result(FlutterError(code:"busy",message:"Picker already open",details:nil));return}
   self.pending=result
   let picker=UIDocumentPickerViewController(forOpeningContentTypes:[.png,.jpeg],asCopy:true)
   picker.delegate=self;self.presenter?.present(picker,animated:true)
  }
 }
 func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController){pending?(nil);pending=nil}
 func documentPicker(_ controller: UIDocumentPickerViewController,didPickDocumentsAt urls:[URL]) {
  let result=pending;pending=nil
  guard let url=urls.first else {result?(nil);return}
  DispatchQueue.global(qos:.userInitiated).async {
   do {
    let size=try url.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 2097153
    guard size>0 && size<=2097152 else {throw CocoaError(.fileReadTooLarge)}
    let data=try Data(contentsOf:url,options:.mappedIfSafe)
    guard data.count<=2097152 else {throw CocoaError(.fileReadTooLarge)}
    DispatchQueue.main.async{result?(FlutterStandardTypedData(bytes:data))}
   }catch{DispatchQueue.main.async{result?(FlutterError(code:"image",message:"Choose PNG or JPEG up to 2 MiB",details:nil))}}
  }
 }
}
