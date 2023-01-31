package io.flutter.plugins.camera

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build.VERSION
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.CameraHelper.Facing.BACK
import com.pedro.encoder.input.video.CameraHelper.Facing.FRONT
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.pedro.rtplibrary.view.LightOpenGlView
import io.flutter.embedding.engine.systemchannels.PlatformChannel.DeviceOrientation
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugins.camera.features.CameraFeatures
import io.flutter.plugins.camera.features.resolution.ResolutionPreset
import java.io.*


class CameraNativeView(
        private var context: Context? = null,
        private var activity: Activity? = null,
        private var enableAudio: Boolean = false,
        private val preset: ResolutionPreset,
        private var cameraName: String,
        private var dartMessenger: DartMessenger? = null,
        private var imageFormatGroup: String? = null,
        private var cameraFeatures: CameraFeatures) :
        PlatformView,
        SurfaceHolder.Callback,
        ConnectCheckerRtmp {

    private val glView = LightOpenGlView(context)
    private val rtmpCamera: RtmpCamera2

    private var isSurfaceCreated = false
    private var fps = 0

    // Current supported outputs.
    private val supportedImageFormats = mapOf(
            "yuv420" to ImageFormat.YUV_420_888,
            "jpeg" to ImageFormat.JPEG
    )

    init {
        glView.isKeepAspectRatio = false
        glView.holder.addCallback(this)
        rtmpCamera = RtmpCamera2(glView, this)
        rtmpCamera.setReTries(10)
        rtmpCamera.setFpsListener { fps = it }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("CameraNativeView", "surfaceCreated")
        isSurfaceCreated = true
        startPreview(cameraName)
    }

    override fun onAuthSuccessRtmp() {
    }

    override fun onNewBitrateRtmp(bitrate: Long) {
    }

    override fun onConnectionSuccessRtmp() {
    }

    override fun onConnectionFailedRtmp(reason: String) {
        activity?.runOnUiThread { //Wait 5s and retry connect stream
            if (rtmpCamera.reTry(5000, reason)) {
                dartMessenger?.send(DartMessenger.RTMPEventType.RTMP_RETRY, reason)
            } else {
                dartMessenger?.send(DartMessenger.RTMPEventType.RTMP_STOPPED, "Failed retry")
                rtmpCamera.stopStream()
            }
        }
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {
    }

    override fun onAuthErrorRtmp() {
        activity?.runOnUiThread {
            dartMessenger?.send(DartMessenger.RTMPEventType.ERROR, "Auth error")
        }
    }

    override fun onDisconnectRtmp() {
        activity?.runOnUiThread {
            dartMessenger?.send(DartMessenger.RTMPEventType.RTMP_STOPPED, "Disconnected")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("CameraNativeView", "surfaceChanged $width $height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("CameraNativeView", "surfaceDestroyed")
    }

    fun close() {
        Log.d("CameraNativeView", "close")
    }

    fun getMaxZoomLevel(): Float {
        return rtmpCamera.zoomRange.upper
    }

    fun getMinZoomLevel(): Float {
        return rtmpCamera.zoomRange.lower
    }

    fun setZoomLevel(result: MethodChannel.Result, zoom: Float) {
        rtmpCamera.zoom = zoom

        result.success(null)
    }

    fun takePicture(filePath: String, result: MethodChannel.Result) {
        Log.d("CameraNativeView", "takePicture filePath: $filePath result: $result")
        val file = File(filePath)
        if (file.exists()) {
            result.error("fileExists", "File at path '$filePath' already exists. Cannot overwrite.", null)
            return
        }
        glView.takePhoto {
            try {
                val outputStream: OutputStream = BufferedOutputStream(FileOutputStream(file))
                it.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.close()
                view.post { result.success(null) }
            } catch (e: IOException) {
                result.error("IOError", "Failed saving image", null)
            }
        }
    }

    fun startVideoRecording(filePath: String, result: MethodChannel.Result, imageStreamChannel: EventChannel?) {
        imageStreamChannel?.let { setStreamHandler(it) }

        val file = File(filePath)
        if (file.exists()) {
            result.error("fileExists", "File at path '$filePath' already exists. Cannot overwrite.", null)
            return
        }
        Log.d("CameraNativeView", "startVideoRecording filePath: $filePath result: $result")

        if (!rtmpCamera.isStreaming) {
            val resolutionFeature = cameraFeatures.resolution
            val videoBitRate = if (VERSION.SDK_INT >= 31) {
                resolutionFeature.recordingProfile.videoProfiles[0].bitrate
            } else {
                resolutionFeature.recordingProfileLegacy.videoBitRate
            }
            val rotation = getRotation(cameraFeatures.sensorOrientation.lockedCaptureOrientation)
            Log.d("CameraNativeView", "orientation: ${cameraFeatures.sensorOrientation.lockedCaptureOrientation}, captureSize: ${resolutionFeature.captureSize}, videoBitRate: " + videoBitRate)
            if (rtmpCamera.prepareAudio(64 * 1024,
                            32000,
                            true,
                            true,
                            true) &&
                    rtmpCamera.prepareVideo(
                            resolutionFeature.captureSize.width,
                            resolutionFeature.captureSize.height,
                            30,
                            videoBitRate,
                            rotation
                    )) {
                // Necessary to stream the correct orientation
                rtmpCamera.glInterface.setStreamRotation((rotation + 270) % 360)
                rtmpCamera.startRecord(filePath)
            } else {
                result.error("videoRecordingFailed", "Error preparing stream, This device cant do it", null)
                return
            }
        } else {
            rtmpCamera.startRecord(filePath)
        }
        result.success(null)
    }

    fun startVideoStreaming(url: String?, result: MethodChannel.Result) {
        Log.d("CameraNativeView", "startVideoStreaming url: $url")
        if (url == null) {
            result.error("startVideoStreaming", "Must specify a url.", null)
            return
        }

        try {
            if (!rtmpCamera.isStreaming) {
                val resolutionFeature = cameraFeatures.resolution
                val videoBitRate = if (VERSION.SDK_INT >= 31) {
                    resolutionFeature.recordingProfile.videoProfiles[0].bitrate
                } else {
                    resolutionFeature.recordingProfileLegacy.videoBitRate
                }
                val rotation = getRotation(cameraFeatures.sensorOrientation.lockedCaptureOrientation)
                Log.d("CameraNativeView", "orientation: ${cameraFeatures.sensorOrientation.lockedCaptureOrientation}, captureSize: ${resolutionFeature.captureSize}, videoBitRate: " + videoBitRate)
                if (rtmpCamera.isRecording || rtmpCamera.prepareAudio() && rtmpCamera.prepareVideo(
                                resolutionFeature.captureSize.width,
                                resolutionFeature.captureSize.height,
                                30,
                                videoBitRate,
                                rotation)) {
                    rtmpCamera.glInterface.setStreamRotation((rotation + 270) % 360)
                    // ready to start streaming
                    rtmpCamera.startStream(url)
                } else {
                    result.error("videoStreamingFailed", "Error preparing stream, This device cant do it", null)
                    return
                }
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("videoStreamingFailed", e.message, null)
        } catch (e: IOException) {
            result.error("videoStreamingFailed", e.message, null)
        }
    }

    private fun getRotation(orientation: DeviceOrientation?): Int {
        return when (orientation) {
            DeviceOrientation.PORTRAIT_UP -> 90
            DeviceOrientation.PORTRAIT_DOWN -> 270
            DeviceOrientation.LANDSCAPE_LEFT -> 0
            DeviceOrientation.LANDSCAPE_RIGHT -> 180
            else -> CameraHelper.getCameraOrientation(context)
        }
    }

    fun startVideoRecordingAndStreaming(filePath: String?, url: String?, result: MethodChannel.Result, imageStreamChannel: EventChannel?) {
        Log.d("CameraNativeView", "startVideoStreaming url: $url")
        if (filePath != null) {
            startVideoRecording(filePath, result, imageStreamChannel)
        }
        startVideoStreaming(url, result)
    }

    fun pauseVideoStreaming(result: MethodChannel.Result) {
        // TODO: Implement pause video streaming
    }

    fun resumeVideoStreaming(result: MethodChannel.Result) {
        // TODO: Implement resume video streaming
    }

    fun stopVideoRecordingOrStreaming(result: MethodChannel.Result) {
        try {
            rtmpCamera.apply {
                if (isStreaming) stopStream()
                if (isRecording) {
                    stopRecord()
                }
            }
            if (!rtmpCamera.isRecording) {
                result.success(null)
            }
        } catch (e: CameraAccessException) {
            result.error("videoRecordingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
        }
    }

    fun stopVideoRecording(result: MethodChannel.Result) {
        try {
            rtmpCamera.apply {
                if (isRecording) {
                    stopRecord()
                }
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("stopVideoRecordingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("stopVideoRecordingFailed", e.message, null)
        }
    }

    fun stopVideoStreaming(result: MethodChannel.Result) {
        try {
            rtmpCamera.apply {
                if (isStreaming) stopStream()
            }
            result.success(null)
        } catch (e: CameraAccessException) {
            result.error("stopVideoStreamingFailed", e.message, null)
        } catch (e: IllegalStateException) {
            result.error("stopVideoStreamingFailed", e.message, null)
        }
    }

    fun pauseVideoRecording(result: MethodChannel.Result) {
        if (!rtmpCamera.isRecording) {
            result.success(null)
            return
        }
        try {
            rtmpCamera.pauseRecord()
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
            return
        }
        result.success(null)
    }

    fun resumeVideoRecording(result: MethodChannel.Result) {
        if (!rtmpCamera.isRecording) {
            result.success(null)
            return
        }
        try {
            rtmpCamera.resumeRecord()
        } catch (e: IllegalStateException) {
            result.error("videoRecordingFailed", e.message, null)
            return
        }
        result.success(null)
    }

    fun startPreviewWithImageStream(imageStreamChannel: EventChannel) {
        setStreamHandler(imageStreamChannel)
    }

    private fun setStreamHandler(imageStreamChannel: EventChannel) {
        imageStreamChannel.setStreamHandler(
                object : EventChannel.StreamHandler {
                    override fun onListen(o: Any?, imageStreamSink: EventSink) {
                        setImageStreamImageAvailableListener(imageStreamSink)
                    }

                    override fun onCancel(o: Any?) {
                        rtmpCamera.removeImageListener()
                    }
                })
    }

    private fun setImageStreamImageAvailableListener(imageStreamSink: EventSink) {
        // For image streaming, use the provided image format or fall back to YUV420.
        var imageFormat = supportedImageFormats[imageFormatGroup]
        if (imageFormat == null) {
            Log.w("CameraNativeView", "The selected imageFormatGroup is not supported by Android. Defaulting to yuv420")
            imageFormat = ImageFormat.YUV_420_888
        }
        rtmpCamera.addImageListener(
                imageFormat,
                2 // This is needed because the underlying API is using acquireLatestImage()
        ) { image ->
            val planes: MutableList<Map<String, Any>> = ArrayList()
            for (plane in image.planes) {
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer[bytes, 0, bytes.size]
                val planeBuffer: MutableMap<String, Any> = HashMap()
                planeBuffer["bytesPerRow"] = plane.rowStride
                planeBuffer["bytesPerPixel"] = plane.pixelStride
                planeBuffer["bytes"] = bytes
                planes.add(planeBuffer)
            }

            val imageBuffer: MutableMap<String, Any?> = HashMap()
            imageBuffer["width"] = image.width
            imageBuffer["height"] = image.height
            imageBuffer["format"] = image.format
            imageBuffer["planes"] = planes

            val handler = Handler(Looper.getMainLooper())
            handler.post { imageStreamSink.success(imageBuffer) }
        }
    }

    fun startPreview(cameraNameArg: String? = null) {
        val targetCamera = if (cameraNameArg.isNullOrEmpty()) {
            cameraName
        } else {
            cameraNameArg
        }
        cameraName = targetCamera

        if (isSurfaceCreated) {
            try {
                if (rtmpCamera.isOnPreview) {
                    rtmpCamera.stopPreview()
                }

                val resolutionFeature = cameraFeatures.resolution
                Log.d("CameraNativeView", "startPreview: $preset, previewSize: ${resolutionFeature.previewSize}, orientation: ${cameraFeatures.sensorOrientation.lockedCaptureOrientation}")
                rtmpCamera.startPreview(
                        if (isFrontFacing(targetCamera)) FRONT else BACK,
                        resolutionFeature.previewSize.width,
                        resolutionFeature.previewSize.height,
                        getRotation(cameraFeatures.sensorOrientation.lockedCaptureOrientation))
            } catch (e: CameraAccessException) {
//                close()
                activity?.runOnUiThread { dartMessenger?.send(DartMessenger.RTMPEventType.ERROR, "CameraAccessException") }
                return
            }
        }
    }

    fun getStreamStatistics(result: MethodChannel.Result) {
        val ret = hashMapOf<String, Any>()
        ret["cacheSize"] = rtmpCamera.cacheSize
        ret["sentAudioFrames"] = rtmpCamera.sentAudioFrames
        ret["sentVideoFrames"] = rtmpCamera.sentVideoFrames
        ret["droppedAudioFrames"] = rtmpCamera.droppedAudioFrames
        ret["droppedVideoFrames"] = rtmpCamera.droppedVideoFrames
        ret["isAudioMuted"] = rtmpCamera.isAudioMuted
        ret["bitrate"] = rtmpCamera.bitrate
        ret["width"] = rtmpCamera.streamWidth
        ret["height"] = rtmpCamera.streamHeight
        ret["fps"] = fps
        result.success(ret)
    }

    override fun getView(): View {
        return glView
    }

    override fun dispose() {
        isSurfaceCreated = false
        context = null
        activity = null
    }

    private fun isFrontFacing(cameraName: String): Boolean {
        val cameraManager = activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val characteristics = cameraManager.getCameraCharacteristics(cameraName)
        return characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
    }
}
