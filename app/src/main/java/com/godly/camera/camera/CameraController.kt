package com.godly.camera.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.godly.camera.permissions.Permissions
import com.godly.camera.processing.EnhanceRunner
import com.godly.camera.storage.MediaStoreSaver
import com.godly.camera.zoom.ZoomEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CamMode(val label: String) {
    PHOTO("Photo"), VIDEO("Video"), NIGHT("Night"), MILKY("Milky Way"), PRO("Pro")
}

data class ProSettings(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val awb: Int? = null,
    val focusDiopter: Float? = null,
)

@OptIn(ExperimentalCamera2Interop::class)
class CameraController(private val app: Context, private val scope: CoroutineScope) {

    val zoom = ZoomEngine(scope)

    var mode by mutableStateOf(CamMode.PHOTO)
        private set
    var lensFacing by mutableIntStateOf(CameraSelector.LENS_FACING_BACK)
        private set
    var caps by mutableStateOf(CameraCaps())
        private set

    // user choices (null = automatic) and the values actually in effect
    var userQuality by mutableStateOf<Quality?>(null)
        private set
    var userFps by mutableStateOf<Int?>(null)
        private set
    var activeQuality by mutableStateOf<Quality?>(null)
        private set
    var activeFps by mutableIntStateOf(30)
        private set

    var flashMode by mutableIntStateOf(ImageCapture.FLASH_MODE_OFF)
        private set
    var message by mutableStateOf<String?>(null)
    var enhanceCopy by mutableStateOf(false)

    var recording by mutableStateOf(false)
        private set
    var recordSeconds by mutableIntStateOf(0)
        private set

    var pro by mutableStateOf(ProSettings())
        private set
    var nightSeconds by mutableStateOf<Int?>(null)
        private set
    var nightBusy by mutableStateOf(false)
        private set
    var nightCountdown by mutableStateOf<Int?>(null)
        private set

    var evSupported by mutableStateOf(false)
        private set
    var evMin by mutableIntStateOf(0)
        private set
    var evMax by mutableIntStateOf(0)
        private set
    var evIndex by mutableIntStateOf(0)
        private set
    var evStep by mutableFloatStateOf(0f)
        private set

    val bindKey: List<Any?> get() = listOf(mode, lensFacing, userQuality, userFps)

    val infoText: String
        get() = if (mode == CamMode.VIDEO && activeQuality != null)
            "${activeQuality!!.label()} · ${activeFps}fps"
        else "${mode.label} · max quality"

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var ticker: Job? = null
    private val mainExecutor = ContextCompat.getMainExecutor(app)

    // ---------------------------------------------------------------- binding

    fun bind(owner: LifecycleOwner, view: PreviewView) {
        val future = ProcessCameraProvider.getInstance(app)
        future.addListener({
            try {
                doBind(future.get(), owner, view)
            } catch (e: Exception) {
                message = "Camera error: ${e.message}"
            }
        }, mainExecutor)
    }

    private fun doBind(p: ProcessCameraProvider, owner: LifecycleOwner, view: PreviewView) {
        p.unbindAll()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val info = selector.filter(p.availableCameraInfos).firstOrNull()
        if (info == null) {
            message = "No camera available for this lens."
            return
        }
        caps = readCaps(info)

        val q = userQuality?.takeIf { it in caps.qualities }
            ?: caps.qualities.firstOrNull { it == Quality.FHD }
            ?: caps.qualities.firstOrNull()
        activeQuality = q
        val fps = userFps?.takeIf { (it == 60 && caps.fps60) || (it == 30 && caps.fps30) }
            ?: if (caps.fps60) 60 else 30
        activeFps = fps

        val pb = Preview.Builder()
        val fpsRange: Range<Int>? = when (mode) {
            CamMode.VIDEO -> pickFpsRange(fps)
            CamMode.NIGHT, CamMode.MILKY -> caps.fpsRanges.minWithOrNull(compareBy<Range<Int>>({ it.lower }, { it.upper }))
            else -> null
        }
        fpsRange?.let {
            Camera2Interop.Extender(pb).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
        }
        val preview = pb.build().also { it.setSurfaceProvider(view.surfaceProvider) }

        val cam: Camera
        if (mode == CamMode.VIDEO && q != null) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(q, FallbackStrategy.lowerQualityOrHigherThan(q)))
                .build()
            val vb = VideoCapture.Builder(recorder)
            fpsRange?.let {
                Camera2Interop.Extender(vb).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
            }
            val vc = vb.build()
            videoCapture = vc
            imageCapture = null
            cam = p.bindToLifecycle(owner, selector, preview, vc)
        } else {
            if (mode == CamMode.VIDEO) message = "Video recording is not reported as supported by this camera."
            val ic = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashMode)
                .build()
            imageCapture = ic
            videoCapture = null
            cam = p.bindToLifecycle(owner, selector, preview, ic)
        }
        camera = cam
        zoom.attach(cam, owner)

        val es = cam.cameraInfo.exposureState
        evSupported = es.isExposureCompensationSupported
        evMin = es.exposureCompensationRange.lower
        evMax = es.exposureCompensationRange.upper
        evStep = es.exposureCompensationStep.toFloat()
        evIndex = 0

        applyManual()
        applyTorch()
    }

    private fun pickFpsRange(fps: Int): Range<Int>? =
        caps.fpsRanges.firstOrNull { it.lower == fps && it.upper == fps }
            ?: caps.fpsRanges.firstOrNull { it.upper == fps && it.lower >= 15 }

    private fun readCaps(info: CameraInfo): CameraCaps {
        val c2 = Camera2CameraInfo.from(info)
        val capabilities = c2.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        val manual = capabilities?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        val minFocus = c2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val qualities = try {
            Recorder.getVideoCapabilities(info).getSupportedQualities(androidx.camera.core.DynamicRange.SDR)
        } catch (e: Exception) {
            emptyList()
        }
        return CameraCaps(
            qualities = qualities,
            fpsRanges = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.toList() ?: emptyList(),
            manualSensor = manual,
            isoRange = if (manual) c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) else null,
            exposureRange = if (manual) c2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) else null,
            manualFocus = minFocus > 0f,
            minFocusDiopter = minFocus,
            awbModes = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList() ?: emptyList(),
            hasFlash = info.hasFlashUnit(),
        )
    }

    // ---------------------------------------------------------------- user actions

    fun setMode(m: CamMode) {
        if (recording || nightBusy) return
        mode = m
        nightSeconds = null
    }

    fun switchLens() {
        if (recording || nightBusy) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
    }

    fun chooseQuality(q: Quality) { if (!recording) userQuality = q }
    fun chooseFps(f: Int) { if (!recording) userFps = f }

    fun cycleFlash() {
        flashMode = if (mode == CamMode.VIDEO) {
            if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        } else when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        applyTorch()
    }

    val flashLabel: String
        get() = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> "Flash AUTO"
            ImageCapture.FLASH_MODE_ON -> if (mode == CamMode.VIDEO) "Torch ON" else "Flash ON"
            else -> if (mode == CamMode.VIDEO) "Torch OFF" else "Flash OFF"
        }

    private fun applyTorch() {
        if (mode == CamMode.VIDEO) camera?.cameraControl?.enableTorch(flashMode != ImageCapture.FLASH_MODE_OFF)
    }

    fun setEv(index: Int) {
        evIndex = index.coerceIn(evMin, evMax)
        camera?.cameraControl?.setExposureCompensationIndex(evIndex)
    }

    fun updatePro(p: ProSettings) { pro = p; applyManual() }
    fun setNightSeconds(s: Int?) { nightSeconds = s; applyManual() }

    /** Applies manual sensor controls only where the camera reports support. */
    fun applyManual() {
        val cam = camera ?: return
        val b = CaptureRequestOptions.Builder()
        var iso: Int? = null
        var exp: Long? = null
        if (mode == CamMode.PRO) {
            iso = pro.iso; exp = pro.exposureNs
        } else if ((mode == CamMode.NIGHT || mode == CamMode.MILKY) && nightSeconds != null) {
            exp = nightSeconds!! * 1_000_000_000L
            iso = if (mode == CamMode.MILKY) 3200 else 1600
        }
        if (caps.manualSensor && (iso != null || exp != null)) {
            val ir = caps.isoRange
            val er = caps.exposureRange
            val i = (iso ?: 400).let { if (ir != null) it.coerceIn(ir.lower, ir.upper) else it }
            val e = (exp ?: 33_333_333L).let { if (er != null) it.coerceIn(er.lower, er.upper) else it }
            b.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, i)
            b.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, e)
            if (e > 33_333_333L) b.setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, e)
        }
        if (mode == CamMode.PRO) {
            pro.awb?.let { b.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, it) }
            if (caps.manualFocus) pro.focusDiopter?.let {
                b.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                b.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, it)
            }
        }
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(b.build())
    }

    // ---------------------------------------------------------------- photo

    fun takePhoto(onDone: () -> Unit = {}) {
        val ic = imageCapture
        if (ic == null) { message = "Camera not ready."; onDone(); return }
        ic.takePicture(MediaStoreSaver.photoOptions(app), mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                message = "Saved to Pictures/GodlyCamera"
                onDone()
                val uri = r.savedUri
                if (enhanceCopy && uri != null) enhance(uri)
            }

            override fun onError(e: ImageCaptureException) {
                message = "Could not save photo: ${e.message}"
                onDone()
            }
        })
    }

    private fun enhance(uri: android.net.Uri) {
        message = "Saved. Creating enhanced copy…"
        scope.launch {
            val out = withContext(Dispatchers.Default) { EnhanceRunner.enhanceCopy(app, uri) }
            message = if (out != null) "Enhanced copy saved (original kept, max 4000 px side)."
            else "Enhanced copy failed (original is safe)."
        }
    }

    fun nightCapture() {
        if (nightBusy) return
        val s = if (caps.manualSensor && nightSeconds != null) nightSeconds!! else 2
        nightBusy = true
        nightCountdown = s
        ticker?.cancel()
        ticker = scope.launch {
            var left = s
            while (left > 0) { delay(1000); left--; nightCountdown = left }
        }
        takePhoto(onDone = { ticker?.cancel(); nightCountdown = null; nightBusy = false })
    }

    // ---------------------------------------------------------------- video

    @SuppressLint("MissingPermission")
    fun startRecording(withAudio: Boolean) {
        val vc = videoCapture
        if (vc == null) { message = "Switch to Video mode first."; return }
        if (recording) return
        var pending = vc.output.prepareRecording(app, MediaStoreSaver.videoOptions(app))
        if (withAudio && Permissions.has(app, android.Manifest.permission.RECORD_AUDIO)) {
            pending = pending.withAudioEnabled()
        }
        activeRecording = pending.start(mainExecutor) { ev ->
            when (ev) {
                is VideoRecordEvent.Start -> { recording = true; recordSeconds = 0 }
                is VideoRecordEvent.Status ->
                    recordSeconds = (ev.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                is VideoRecordEvent.Finalize -> {
                    recording = false
                    activeRecording = null
                    message = if (ev.hasError()) "Video save problem (code ${ev.error})"
                    else "Saved to Movies/GodlyCamera"
                }
                else -> {}
            }
        }
    }

    fun stopRecording() { activeRecording?.stop() }

    fun release() {
        activeRecording?.stop()
        ticker?.cancel()
    }
}
