package io.flutter.plugins.camera

import android.app.Activity
import android.content.Context
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.flutter.plugins.camera.features.CameraFeatures
import io.flutter.plugins.camera.features.resolution.ResolutionPreset

internal class NativeViewFactory(private val activity: Activity) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    var cameraNativeView: CameraNativeView? = null
    var cameraName: String = "0"
    var preset: ResolutionPreset = ResolutionPreset.low
    var enableAudio: Boolean = false
    var dartMessenger: DartMessenger? = null
    var cameraFeatures: CameraFeatures? = null
    var imageFormatGroup: String? = null

    override fun create(context: Context, id: Int, args: Any?): PlatformView {
        cameraNativeView = CameraNativeView(context = context,
                activity = activity,
                enableAudio = enableAudio,
                preset = preset,
                cameraName = cameraName,
                dartMessenger = dartMessenger,
                imageFormatGroup = imageFormatGroup,
                cameraFeatures = cameraFeatures!!)
        return cameraNativeView!!
    }
}