package com.saferesale.app.ui.screens.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import com.saferesale.app.domain.model.DisplayInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlin.math.sqrt

@HiltViewModel
class DisplayViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _info = MutableStateFlow(buildDisplayInfo())
    val info: StateFlow<DisplayInfo> = _info

    private fun buildDisplayInfo(): DisplayInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        val display = @Suppress("DEPRECATION") wm.defaultDisplay

        val refreshRate = display.refreshRate
        val supportedRates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display.supportedModes.map { it.refreshRate }.distinct().sorted()
        } else listOf(refreshRate)

        val hdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            display.hdrCapabilities?.supportedHdrTypes
                ?.map { when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR $it" } }
                ?: emptyList()
        } else emptyList()

        val wInch = dm.widthPixels / dm.xdpi
        val hInch = dm.heightPixels / dm.ydpi
        val diag  = sqrt((wInch * wInch + hInch * hInch).toDouble()).toFloat()

        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0)
                .toFloat() / 255f * 100f
        } catch (e: Exception) { 0f }

        val orientation = if (dm.widthPixels > dm.heightPixels) "Landscape" else "Portrait"

        return DisplayInfo(
            widthPx = dm.widthPixels, heightPx = dm.heightPixels,
            densityDpi = dm.densityDpi, refreshRateHz = refreshRate,
            supportedRefreshRates = supportedRates,
            hdrCapabilities = hdrTypes, hasHdr = hdrTypes.isNotEmpty(),
            hasWideColorGamut = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                display.isWideColorGamut else false,
            physicalWidthInch = wInch, physicalHeightInch = hInch,
            diagonalInch = diag, xdpi = dm.xdpi, ydpi = dm.ydpi,
            orientation = orientation, brightnessPercent = brightness,
        )
    }
}
