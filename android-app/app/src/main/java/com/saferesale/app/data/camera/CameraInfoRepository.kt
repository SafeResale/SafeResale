package com.saferesale.app.data.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import com.saferesale.app.domain.model.CameraDetail
import com.saferesale.app.domain.model.CameraInfo

class CameraInfoRepository constructor(
    private val context: Context
) {
    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    fun getCameraInfo(): CameraInfo {
        val cameras = try {
            cameraManager.cameraIdList.map { id -> buildCameraDetail(id) }
        } catch (e: Exception) { emptyList() }
        return CameraInfo(cameras = cameras)
    }

    private fun buildCameraDetail(id: String): CameraDetail {
        val chars = cameraManager.getCameraCharacteristics(id)

        val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT    -> "Front"
            CameraCharacteristics.LENS_FACING_BACK     -> "Rear"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        val streamMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maxSize: Size? = streamMap?.getOutputSizes(ImageFormat.JPEG)
            ?.maxByOrNull { it.width.toLong() * it.height }
        val mpx = (maxSize?.let { it.width.toLong() * it.height / 1_000_000.0 })?.let {
            "%.1f".format(it).toFloat()
        } ?: 0f

        val fpsList = streamMap?.highSpeedVideoFpsRanges
            ?.map { it.upper }
            ?.distinct()
            ?.sorted() ?: emptyList()

        val formats = streamMap?.outputFormats
            ?.map { imageFormatName(it) }
            ?.distinct() ?: emptyList()

        val hasFlash  = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val hasAf     = (chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.size ?: 0) > 1
        val hasOis    = (chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.size ?: 0) > 1

        val apertures    = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList() ?: emptyList()
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList() ?: emptyList()
        val zoomMax      = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f

        val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY     -> "Legacy"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED    -> "Limited"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL       -> "Full"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3          -> "Level 3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL   -> "External"
            else -> "Unknown"
        }

        val stabModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.map { if (it == 1) "Video EIS" else "Off" } ?: emptyList()

        return CameraDetail(
            id = id, facing = facing,
            megaPixels = mpx,
            maxWidth = maxSize?.width ?: 0, maxHeight = maxSize?.height ?: 0,
            supportedFps = fpsList,
            supportedFormats = formats,
            hasFlash = hasFlash, hasAutoFocus = hasAf, hasOIS = hasOis, hasOIS2 = false,
            apertureList = apertures, focalLengths = focalLengths,
            digitalZoomMax = zoomMax,
            supportedStabilizationModes = stabModes,
            hardwareLevel = hwLevel,
        )
    }

    private fun imageFormatName(format: Int): String = when (format) {
        ImageFormat.JPEG      -> "JPEG"
        ImageFormat.RAW_SENSOR-> "RAW"
        ImageFormat.YUV_420_888 -> "YUV 420"
        ImageFormat.HEIC      -> "HEIC"
        ImageFormat.DEPTH_JPEG-> "Depth JPEG"
        else -> "Format $format"
    }
}