package com.saferesale.app.domain.model

data class CameraInfo(
    val cameras: List<CameraDetail> = emptyList(),
)

data class CameraDetail(
    val id: String,
    val facing: String,              // Front / Back / External
    val megaPixels: Float,
    val maxWidth: Int,
    val maxHeight: Int,
    val supportedFps: List<Int>,
    val supportedFormats: List<String>,
    val hasFlash: Boolean,
    val hasAutoFocus: Boolean,
    val hasOIS: Boolean,
    val hasOIS2: Boolean,
    val apertureList: List<Float>,
    val focalLengths: List<Float>,
    val digitalZoomMax: Float,
    val supportedStabilizationModes: List<String>,
    val hardwareLevel: String,
)