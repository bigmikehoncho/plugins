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

    override fun create(context: Context, id: Int, args: Any?): PlatformView {
        cameraNativeView = CameraNativeView(context, activity, enableAudio, preset, cameraName, dartMessenger)
        return cameraNativeView!!
    }
}