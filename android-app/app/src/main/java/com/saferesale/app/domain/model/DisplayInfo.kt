package com.saferesale.app.domain.model

data class DisplayInfo(
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val densityDpi: Int = 0,
    val refreshRateHz: Float = 0f,
    val supportedRefreshRates: List<Float> = emptyList(),
    val hdrCapabilities: List<String> = emptyList(),
    val hasHdr: Boolean = false,
    val hasWideColorGamut: Boolean = false,
    val physicalWidthInch: Float = 0f,
    val physicalHeightInch: Float = 0f,
    val diagonalInch: Float = 0f,
    val xdpi: Float = 0f,
    val ydpi: Float = 0f,
    val orientation: String = "",
    val brightnessPercent: Float = 0f,
    val isAdaptiveBrightness: Boolean = false,
    val colorMode: String = "",
)
