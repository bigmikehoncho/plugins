// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package io.flutter.plugins.camera

import android.app.Activity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugins.camera.CameraPermissions.PermissionsRegistry
import io.flutter.view.TextureRegistry
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.EventChannel
import android.hardware.camera2.CameraAccessException
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.OrientationEventListener
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.platform.PlatformViewRegistry
import io.flutter.plugins.camera.features.CameraFeatureFactoryImpl
import io.flutter.plugins.camera.features.CameraFeatures
import io.flutter.plugins.camera.features.autofocus.FocusMode
import io.flutter.plugins.camera.features.exposurelock.ExposureMode
import io.flutter.plugins.camera.features.resolution.ResolutionPreset
import java.lang.Exception
import java.lang.RuntimeException
import java.util.HashMap

internal class MethodCallHandlerImpl(
        private val activity: Activity,
        private val messenger: BinaryMessenger,
        private val cameraPermissions: CameraPermissions,
        private val permissionsRegistry: PermissionsRegistry,
        private val textureRegistry: TextureRegistry,
        platformViewRegistry: PlatformViewRegistry) : MethodCallHandler {

    private val methodChannel: MethodChannel = MethodChannel(messenger, "plugins.flutter.io/camera_android")
    private val imageStreamChannel: EventChannel = EventChannel(messenger, "plugins.flutter.io/camera_android/imageStream")

    private var nativeViewFactory = NativeViewFactory(activity)
    private var currentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN

    init {
        platformViewRegistry
                .registerViewFactory("hybrid-view-type", nativeViewFactory) // fixme
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        Log.d("MethodCallHandlerImpl", call.method)
        when (call.method) {
            "availableCameras" -> try {
                result.success(CameraUtilsRTMP.getAvailableCameras(activity))
            } catch (e: Exception) {
                handleException(e, result)
            }
            "create" -> {
                getCameraView()?.close()
                cameraPermissions.requestPermissions(
                        activity,
                        permissionsRegistry,
                        call.argument("enableAudio")!!
                ) { errCode: String?, errDesc: String? ->
                    if (errCode == null) {
                        try {
                            instantiateCamera(call, result)
                        } catch (e: Exception) {
                            handleException(e, result)
                        }
                    } else {
                        result.error(errCode, errDesc, null)
                    }
                }
            }
            "initialize" -> {
//                val camera = getCameraView()
//                if (camera != null) {
//                    try {
//                        camera.open(call.argument("imageFormatGroup"))
//                        result.success(null)
//                    } catch (e: Exception) {
//                        handleException(e, result)
//                    }
//                } else {
//                    result.error(
//                            "cameraNotFound",
//                            "Camera not found. Please call the 'create' method before calling 'initialize'.",
//                            null)
//                }

                nativeViewFactory.imageFormatGroup = call.argument("imageFormatGroup")
                nativeViewFactory.cameraFeatures?.apply {
                    nativeViewFactory.dartMessenger?.sendCameraInitializedEvent(
                            resolution.previewSize.width,
                            resolution.previewSize.height,
                            ExposureMode.auto, // TODO add real values for these last 4 params
                            FocusMode.auto,
                            false,
                            false
                    )
                }
                getCameraView()?.startPreview(nativeViewFactory.cameraName)
                result.success(null)
            }
            "takePicture" -> {
                // TODO
//                getCameraView()!!.takePicture(result)
            }
            "prepareForVideoRecording" -> {

                // This optimization is not required for Android.
                result.success(null)
            }
            "startVideoRecording" -> {
                getCameraView()!!.startVideoRecording(result)
            }
            "stopVideoRecording" -> {
                getCameraView()!!.stopVideoRecording(result)
            }
            "pauseVideoRecording" -> {
                getCameraView()!!.pauseVideoRecording(result)
            }
            "resumeVideoRecording" -> {
                getCameraView()!!.resumeVideoRecording(result)
            }
            "startVideoStreaming" -> {
                Log.i("Stuff", "startVideoStreaming ${call.arguments}")
                getCameraView()?.startVideoStreaming(
                        call.argument("url"),
                        result)
            }
            "startVideoRecordingAndStreaming" -> {
                Log.i("Stuff", "startVideoRecordingAndStreaming ${call.arguments}")
                getCameraView()?.startVideoRecordingAndStreaming(
                        call.argument("url"),
                        result)
            }
            "pauseVideoStreaming" -> {
                Log.i("Stuff", "pauseVideoStreaming")
                getCameraView()?.pauseVideoStreaming(result)
            }
            "resumeVideoStreaming" -> {
                Log.i("Stuff", "resumeVideoStreaming")
                getCameraView()?.resumeVideoStreaming(result)
            }
            "stopRecordingOrStreaming" -> {
                Log.i("Stuff", "stopRecordingOrStreaming")
                getCameraView()?.stopVideoRecordingOrStreaming(result)
            }
            "stopRecording" -> {
                Log.i("Stuff", "stopRecording")
                getCameraView()?.stopVideoRecording(result)
            }
            "stopStreaming" -> {
                Log.i("Stuff", "stopStreaming")
                getCameraView()?.stopVideoStreaming(result)
            }
            "getStreamStatistics" -> {
                Log.i("Stuff", "getStreamStatistics")
                try {
                    getCameraView()?.getStreamStatistics(result)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "setFlashMode" -> {
//                val modeStr = call.argument<String>("mode")
//                val mode = FlashMode.getValueForString(modeStr)
//                if (mode == null) {
//                    result.error("setFlashModeFailed", "Unknown flash mode $modeStr", null)
//                    return
//                }
//                try {
//                    camera!!.setFlashMode(result, mode)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "setExposureMode" -> {
//                val modeStr = call.argument<String>("mode")
//                val mode = ExposureMode.getValueForString(modeStr)
//                if (mode == null) {
//                    result.error("setExposureModeFailed", "Unknown exposure mode $modeStr", null)
//                    return
//                }
//                try {
//                    camera!!.setExposureMode(result, mode)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "setExposurePoint" -> {
//                val reset = call.argument<Boolean>("reset")
//                var x: Double? = null
//                var y: Double? = null
//                if (reset == null || !reset) {
//                    x = call.argument("x")
//                    y = call.argument("y")
//                }
//                try {
//                    camera!!.setExposurePoint(result, Point(x, y))
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "getMinExposureOffset" -> {
//                try {
//                    result.success(camera!!.minExposureOffset)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "getMaxExposureOffset" -> {
//                try {
//                    result.success(camera!!.maxExposureOffset)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "getExposureOffsetStepSize" -> {
//                try {
//                    result.success(camera!!.exposureOffsetStepSize)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "setExposureOffset" -> {
//                try {
//                    camera!!.setExposureOffset(result, call.argument("offset")!!)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "setFocusMode" -> {
//                val modeStr = call.argument<String>("mode")
//                val mode = FocusMode.getValueForString(modeStr)
//                if (mode == null) {
//                    result.error("setFocusModeFailed", "Unknown focus mode $modeStr", null)
//                    return
//                }
//                try {
//                    camera!!.setFocusMode(result, mode)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "setFocusPoint" -> {
//                val reset = call.argument<Boolean>("reset")
//                var x: Double? = null
//                var y: Double? = null
//                if (reset == null || !reset) {
//                    x = call.argument("x")
//                    y = call.argument("y")
//                }
//                try {
//                    camera!!.setFocusPoint(result, Point(x, y))
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "startImageStream" -> {
                try {
                    getCameraView()!!.startPreviewWithImageStream(imageStreamChannel)
                    result.success(null)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "stopImageStream" -> {
                try {
                    getCameraView()!!.startPreview()
                    result.success(null)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "getMaxZoomLevel" -> {
//                assert(camera != null)
//                try {
//                    val maxZoomLevel = camera!!.maxZoomLevel
//                    result.success(maxZoomLevel)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "getMinZoomLevel" -> {
//                assert(camera != null)
//                try {
//                    val minZoomLevel = camera!!.minZoomLevel
//                    result.success(minZoomLevel)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "setZoomLevel" -> {
//                assert(camera != null)
//                val zoom = call.argument<Double>("zoom")
//                if (zoom == null) {
//                    result.error(
//                            "ZOOM_ERROR", "setZoomLevel is called without specifying a zoom level.", null)
//                    return
//                }
//                try {
//                    camera!!.setZoomLevel(result, zoom.toFloat())
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "lockCaptureOrientation" -> {
                val orientation = CameraUtils.deserializeDeviceOrientation(call.argument("orientation"))
                try {
                    getCameraView()!!.lockCaptureOrientation(orientation)
                    result.success(null)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "unlockCaptureOrientation" -> {
                try {
                    getCameraView()!!.unlockCaptureOrientation()
                    result.success(null)
                } catch (e: Exception) {
                    handleException(e, result)
                }
            }
            "pausePreview" -> {
//                try {
//                    camera!!.pausePreview()
//                    result.success(null)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "resumePreview" -> {
//                camera!!.resumePreview()
//                result.success(null)
            }
            "dispose" -> {
//                if (camera != null) {
//                    camera!!.dispose()
//                }
//                result.success(null)

                // Native camera view handles the view lifecycle by themselves
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    fun stopListening() {
        methodChannel.setMethodCallHandler(null)
    }

//    @Throws(CameraAccessException::class)
//    private fun instantiateCamera(call: MethodCall, result: MethodChannel.Result) {
//        val cameraName = call.argument<String>("cameraName")
//        val preset = call.argument<String>("resolutionPreset")
//        val enableAudio = call.argument<Boolean>("enableAudio")!!
//        val flutterSurfaceTexture = textureRegistry.createSurfaceTexture()
//        val dartMessenger = DartMessenger(
//                messenger, flutterSurfaceTexture.id(), Handler(Looper.getMainLooper()))
//        val cameraProperties: CameraProperties = CameraPropertiesImpl(cameraName, CameraUtils.getCameraManager(activity))
//        val resolutionPreset = ResolutionPreset.valueOf(preset!!)
//        camera = Camera(
//                activity,
//                flutterSurfaceTexture,
//                CameraFeatureFactoryImpl(),
//                dartMessenger,
//                cameraProperties,
//                resolutionPreset,
//                enableAudio)
//        val reply: MutableMap<String, Any> = HashMap()
//        reply["cameraId"] = flutterSurfaceTexture.id()
//        result.success(reply)
//    }

    @Throws(CameraAccessException::class)
    private fun instantiateCamera(call: MethodCall, result: MethodChannel.Result) {
            val cameraName = call.argument<String>("cameraName") ?: "0"
            val resolutionPreset = call.argument<String>("resolutionPreset")
            val enableAudio = call.argument<Boolean>("enableAudio")!!
        val flutterSurfaceTexture = textureRegistry.createSurfaceTexture()
        val textureId = flutterSurfaceTexture.id()
            val dartMessenger = DartMessenger(messenger, textureId, Handler(Looper.getMainLooper()))

            val preset = ResolutionPreset.valueOf(resolutionPreset!!)
            val previewSize = CameraUtilsRTMP.computeBestPreviewSize(cameraName, preset)
            val reply: MutableMap<String, Any> = HashMap()
            reply["cameraId"] = textureId
            reply["previewWidth"] = previewSize.width
            reply["previewHeight"] = previewSize.height
            reply["previewQuarterTurns"] = currentOrientation / 90
//            Log.i("TAG", "open: width: " + reply["previewWidth"] + " height: " + reply["previewHeight"] + " currentOrientation: " + currentOrientation + " quarterTurns: " + reply["previewQuarterTurns"])
            // TODO Refactor cameraView initialisation
            nativeViewFactory.cameraName = cameraName
            nativeViewFactory.preset = preset
            nativeViewFactory.enableAudio = enableAudio
            nativeViewFactory.dartMessenger = dartMessenger
        nativeViewFactory.cameraFeatures = CameraFeatures.init(
                CameraFeatureFactoryImpl(),
                CameraPropertiesImpl(cameraName, CameraUtils.getCameraManager(activity)),
                activity,
                dartMessenger,
                preset
        )
            result.success(reply)
    }

    // We move catching CameraAccessException out of onMethodCall because it causes a crash
    // on plugin registration for sdks incompatible with Camera2 (< 21). We want this plugin to
    // to be able to compile with <21 sdks for apps that want the camera and support earlier version.
    private fun handleException(exception: Exception, result: MethodChannel.Result) {
        if (exception is CameraAccessException) {
            result.error("CameraAccess", exception.message, null)
            return
        }
        throw (exception as RuntimeException)
    }

    init {
        methodChannel.setMethodCallHandler(this)
    }

    private fun getCameraView(): CameraNativeView? = nativeViewFactory.cameraNativeView
}