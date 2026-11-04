package com.godly.camera.camera

import android.util.Range
import androidx.camera.video.Quality

/** What the selected camera REALLY reports. The UI only offers what is in here. */
data class CameraCaps(
    val qualities: List<Quality> = emptyList(),
    val fpsRanges: List<Range<Int>> = emptyList(),
    val manualSensor: Boolean = false,
    val isoRange: Range<Int>? = null,
    val exposureRange: Range<Long>? = null,
    val manualFocus: Boolean = false,
    val minFocusDiopter: Float = 0f,
    val awbModes: List<Int> = emptyList(),
    val hasFlash: Boolean = false,
) {
    val fps30: Boolean get() = fpsRanges.any { it.upper >= 30 }
    val fps60: Boolean get() = fpsRanges.any { it.upper >= 60 }
}

fun Quality.label(): String = when (this) {
    Quality.UHD -> "4K"
    Quality.FHD -> "1080p"
    Quality.HD -> "720p"
    Quality.SD -> "480p"
    else -> toString()
}
