package com.godly.camera.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godly.camera.camera.CamMode
import com.godly.camera.camera.CameraController
import com.godly.camera.camera.ProSettings
import com.godly.camera.camera.label
import com.godly.camera.zoom.ZoomEngine
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun GlassChip(text: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text,
        color = if (enabled) Color.White else Color(0x66FFFFFF),
        fontSize = 12.sp,
        modifier = Modifier
            .then(if (selected) Modifier.shadow(8.dp, shape, ambientColor = GodlyColors.Blue, spotColor = GodlyColors.Blue) else Modifier)
            .clip(shape)
            .background(if (selected) GodlyColors.Blue.copy(alpha = 0.55f) else GodlyColors.Glass)
            .border(1.dp, if (selected) GodlyColors.Blue else GodlyColors.GlassBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun Shutter(video: Boolean, recording: Boolean, busy: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, tween(90), label = "shutter")
    Box(
        Modifier
            .size(84.dp)
            .scale(scale)
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (video) GodlyColors.Red else GodlyColors.Orange)
            .clickable(interactionSource = src, indication = null, enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(24.dp).background(Color.White, RoundedCornerShape(5.dp)))
        else if (!video) Box(Modifier.size(14.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun RecordingIndicator(seconds: Int) {
    val t = rememberInfiniteTransition(label = "rec")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).alpha(a).background(GodlyColors.Red, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("REC %02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 16.sp)
    }
}

@Composable
fun ZoomSelector(z: ZoomEngine, video: Boolean) {
    val presets = remember(z.minRatio, z.maxRatio, video) {
        buildList {
            if (z.minRatio < 0.95f) add(z.minRatio)
            listOf(1f, 2f, 3f, 5f, 10f, 20f, 30f)
                .filter { (video || it <= 10f) && it >= z.minRatio && it <= z.maxRatio + 0.01f }
                .forEach { add(it) }
        }
    }
    val selected = presets.minByOrNull { abs(ln(z.ratio / it)) }?.takeIf { abs(ln(z.ratio / it)) < 0.06f }
    Column(Modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%.1fx".format(z.ratio), color = Color.White, fontSize = 18.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { p ->
                GlassChip(
                    if (p < 1f) "%.1fx".format(p) else "${p.roundToInt()}x",
                    selected = selected == p,
                ) { z.animateTo(p) }
            }
        }
        Slider(
            value = z.sliderPosition,
            onValueChange = { z.fromSlider(it) },
            enabled = z.maxRatio > z.minRatio,
        )
        Text(
            "Camera-reported zoom range %.1fx–%.1fx. Optical vs digital is not reported by Android; treat anything above 1x as digital/hybrid.".format(z.minRatio, z.maxRatio),
            color = GodlyColors.Dim, fontSize = 10.sp,
        )
    }
}

@Composable
fun ModeBar(c: CameraController) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CamMode.values().forEach { m ->
            val sel = c.mode == m
            Text(
                m.label,
                color = if (sel) GodlyColors.Blue else Color.White,
                fontSize = if (sel) 14.sp else 12.sp,
                modifier = Modifier.clickable { c.setMode(m) }.padding(8.dp),
            )
        }
    }
}

@Composable
fun NightPanel(c: CameraController) {
    val milky = c.mode == CamMode.MILKY
    val er = c.caps.exposureRange
    Column(Modifier.padding(horizontal = 12.dp).fillMaxWidth().glass().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (milky) "Milky Way mode — real results depend on sensor, lens, exposure, tripod and processing. A phone camera cannot capture a detailed Milky Way everywhere; you need a very dark sky and a tripod."
            else "Night mode — uses long exposure only if this camera allows manual exposure. No fake long exposure.",
            color = Color.White, fontSize = 12.sp,
        )
        if (c.caps.manualSensor && er != null) {
            val maxS = (er.upper / 1_000_000_000L).toInt()
            val opts = (if (milky) listOf(4, 8, 15, 30) else listOf(1, 2, 4)).filter { it <= maxS }
            Text("Manual exposure supported up to ${fmtExposure(er.upper)}.", color = GodlyColors.Dim, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassChip("Auto", selected = c.nightSeconds == null) { c.setNightSeconds(null) }
                opts.forEach { s -> GlassChip("${s}s", selected = c.nightSeconds == s) { c.setNightSeconds(s) } }
            }
            if (opts.isEmpty()) Text("This camera's maximum exposure is below the options offered here.", color = GodlyColors.Dim, fontSize = 11.sp)
        } else {
            Text("Manual exposure: not supported by this camera. Using auto exposure with a low-frame-rate range.", color = GodlyColors.Dim, fontSize = 11.sp)
        }
        Text("Use a tripod and keep still during the countdown.", color = GodlyColors.Dim, fontSize = 11.sp)
    }
}

@Composable
fun ProPanel(c: CameraController) {
    val caps = c.caps
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().glass().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val iso = caps.isoRange
        if (caps.manualSensor && iso != null) {
            SliderRow(
                "ISO ${c.pro.iso ?: "Auto"}", (c.pro.iso ?: iso.lower).toFloat(),
                iso.lower.toFloat()..iso.upper.toFloat(), c.pro.iso == null,
                { c.updatePro(c.pro.copy(iso = it.roundToInt())) }, { c.updatePro(c.pro.copy(iso = null)) },
            )
        } else DisabledLine("ISO — not supported on this camera")

        val er = caps.exposureRange
        if (caps.manualSensor && er != null) {
            val lo = er.lower.toDouble(); val hi = er.upper.toDouble()
            val cur = (c.pro.exposureNs ?: er.lower).toDouble()
            SliderRow(
                "Shutter ${c.pro.exposureNs?.let { fmtExposure(it) } ?: "Auto"}",
                (ln(cur / lo) / ln(hi / lo)).toFloat(), 0f..1f, c.pro.exposureNs == null,
                { p -> c.updatePro(c.pro.copy(exposureNs = (lo * (hi / lo).pow(p.toDouble())).toLong())) },
                { c.updatePro(c.pro.copy(exposureNs = null)) },
            )
        } else DisabledLine("Exposure time — not supported on this camera")

        val wb = listOf("Auto" to 1, "Incand." to 2, "Fluor." to 3, "Daylight" to 5, "Cloudy" to 6)
            .filter { it.second in caps.awbModes }
        if (wb.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("WB", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp))
                wb.forEach { (name, mode) ->
                    GlassChip(name, selected = (c.pro.awb ?: 1) == mode) {
                        c.updatePro(c.pro.copy(awb = if (mode == 1) null else mode))
                    }
                }
            }
        } else DisabledLine("White balance — not supported on this camera")

        if (caps.manualFocus) {
            SliderRow(
                "Focus " + (c.pro.focusDiopter?.let { if (it < 0.05f) "∞" else "%.0f cm".format(100f / it) } ?: "Auto"),
                c.pro.focusDiopter ?: 0f, 0f..caps.minFocusDiopter, c.pro.focusDiopter == null,
                { c.updatePro(c.pro.copy(focusDiopter = it)) }, { c.updatePro(c.pro.copy(focusDiopter = null)) },
            )
        } else DisabledLine("Focus distance — not supported on this camera")

        if (c.evSupported && c.evMax > c.evMin) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EV %.1f".format(c.evIndex * c.evStep), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(110.dp))
                Slider(
                    value = c.evIndex.toFloat(), onValueChange = { c.setEv(it.roundToInt()) },
                    valueRange = c.evMin.toFloat()..c.evMax.toFloat(), steps = (c.evMax - c.evMin - 1).coerceAtLeast(0),
                    modifier = Modifier.weight(1f),
                )
            }
        } else DisabledLine("Exposure compensation — not supported on this camera")

        ResolutionFpsRows(c)
    }
}

@Composable
private fun SliderRow(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, isAuto: Boolean,
    onChange: (Float) -> Unit, onAuto: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(110.dp))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        GlassChip("Auto", selected = isAuto, onClick = onAuto)
    }
}

@Composable
private fun DisabledLine(text: String) {
    Text(text, color = Color(0x66FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
fun ResolutionFpsRows(c: CameraController) {
    Text("Video resolution / FPS (Video mode)", color = GodlyColors.Dim, fontSize = 11.sp)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.caps.qualities.isEmpty()) DisabledLine("No video qualities reported")
        c.caps.qualities.forEach { q -> GlassChip(q.label(), selected = c.activeQuality == q) { c.chooseQuality(q) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GlassChip("30 fps", selected = c.activeFps == 30, enabled = c.caps.fps30) { c.chooseFps(30) }
        GlassChip(
            if (c.caps.fps60) "60 fps (unverified)" else "60 fps — unsupported",
            selected = c.activeFps == 60 && c.caps.fps60, enabled = c.caps.fps60,
        ) { c.chooseFps(60) }
    }
}

@Composable
fun SettingsDialog(c: CameraController, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Also save an enhanced copy of each photo", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = c.enhanceCopy, onCheckedChange = { c.enhanceCopy = it })
                }
                Text(
                    "Enhancement = light sharpen only (no AI model is bundled). Runs on-device, the original is never overwritten, " +
                        "and large photos are processed at up to 4000 px on the long side. Nothing is sent to any server.",
                    fontSize = 11.sp,
                )
                ResolutionFpsRows(c)
            }
        },
    )
}

fun fmtExposure(ns: Long): String =
    if (ns >= 1_000_000_000L) "%.1fs".format(ns / 1e9) else "1/${(1e9 / ns).roundToInt()}s"
