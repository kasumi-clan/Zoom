package com.godly.camera.zoom

import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Drives the REAL camera zoom (CameraControl.setZoomRatio). Nothing is faked in the UI.
 * Interpolation is done in log space so 1x->2x feels as fast as 10x->20x.
 * Min/max come from the camera; we cannot tell optical from digital zoom, so the UI says so.
 */
class ZoomEngine(private val scope: CoroutineScope) {
    var minRatio by mutableFloatStateOf(1f)
        private set
    var maxRatio by mutableFloatStateOf(1f)
        private set
    var ratio by mutableFloatStateOf(1f)
        private set

    private var control: CameraControl? = null
    private var job: Job? = null
    private var target = 1f
    private var synced = false

    fun attach(camera: Camera, owner: LifecycleOwner) {
        job?.cancel()
        control = camera.cameraControl
        synced = false
        camera.cameraInfo.zoomState.observe(owner) { s ->
            minRatio = s.minZoomRatio
            maxRatio = s.maxZoomRatio
            if (!synced) {
                synced = true
                ratio = s.zoomRatio
                target = ratio
            }
        }
    }

    val sliderPosition: Float
        get() = if (maxRatio <= minRatio) 0f
        else (ln(ratio / minRatio) / ln(maxRatio / minRatio)).coerceIn(0f, 1f)

    /** Smooth animated move (used by preset buttons). */
    fun animateTo(t: Float) {
        target = t.coerceIn(minRatio, maxRatio)
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                val d = ln(target / ratio)
                if (abs(d) < 0.004f) { apply(target); break }
                apply(ratio * exp(d * 0.16f))
                delay(16)
            }
        }
    }

    fun setImmediate(t: Float) {
        job?.cancel()
        target = t.coerceIn(minRatio, maxRatio)
        apply(target)
    }

    fun pinch(factor: Float) = setImmediate(ratio * factor)

    fun fromSlider(p: Float) = setImmediate(minRatio * (maxRatio / minRatio).pow(p.coerceIn(0f, 1f)))

    private fun apply(r: Float) {
        ratio = r.coerceIn(minRatio, maxRatio)
        control?.setZoomRatio(ratio)
    }
}
