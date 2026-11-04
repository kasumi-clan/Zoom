package com.godly.camera.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.godly.camera.camera.CamMode
import com.godly.camera.camera.CameraController
import com.godly.camera.permissions.Permissions
import kotlinx.coroutines.delay

@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val c = remember { CameraController(ctx.applicationContext, scope) }

    var camGranted by remember { mutableStateOf(Permissions.has(ctx, Manifest.permission.CAMERA)) }
    var camDenied by remember { mutableStateOf(false) }
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        camGranted = it
        camDenied = !it
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) c.message = "Microphone denied: recording video without sound."
        c.startRecording(ok)
    }
    DisposableEffect(Unit) { onDispose { c.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!camGranted) {
            PermissionGate(denied = camDenied) { camLauncher.launch(Manifest.permission.CAMERA) }
        } else {
            CameraContent(c, owner) { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }
}

@Composable
private fun PermissionGate(denied: Boolean, onRequest: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access needed", color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "Godly Camera uses the camera for the live preview, photos and video. " +
                "The microphone is requested later, only when you record video with sound. " +
                "Nothing is uploaded anywhere.",
            color = GodlyColors.Dim, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text(if (denied) "Try again" else "Allow camera") }
        if (denied) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { Permissions.openAppSettings(ctx) }) { Text("Open app settings") }
        }
    }
}

@Composable
private fun CameraContent(c: CameraController, owner: LifecycleOwner, requestAudio: () -> Unit) {
    val ctx = LocalContext.current
    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    LaunchedEffect(c.bindKey) { c.bind(owner, previewView) }
    LaunchedEffect(c.message) {
        if (c.message != null) { delay(3500); c.message = null }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    // pinch-to-zoom drives the real camera zoom
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, _, z, _ -> if (z != 1f) c.zoom.pinch(z) }
        }
    )

    val onShutter: () -> Unit = {
        when (c.mode) {
            CamMode.VIDEO ->
                if (c.recording) c.stopRecording()
                else if (Permissions.has(ctx, Manifest.permission.RECORD_AUDIO)) c.startRecording(true)
                else showAudioDialog = true
            CamMode.NIGHT, CamMode.MILKY -> c.nightCapture()
            else -> c.takePhoto()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // ---- top controls
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (c.caps.hasFlash) GlassChip(c.flashLabel) { c.cycleFlash() } else Spacer(Modifier.height(1.dp))
            GlassChip(c.infoText, selected = true) {}
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GlassChip("Settings") { showSettings = true }
                GlassChip("Flip", enabled = !c.recording && !c.nightBusy) { c.switchLens() }
            }
        }
        c.message?.let {
            Text(
                it, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).glass(14.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        c.nightCountdown?.let {
            Text(
                "Hold still… ${it}s", color = Color.White, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).glass(14.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))

        // ---- mode specific panels
        when (c.mode) {
            CamMode.NIGHT, CamMode.MILKY -> NightPanel(c)
            CamMode.PRO -> ProPanel(c)
            else -> {}
        }

        ZoomSelector(c.zoom, video = c.mode == CamMode.VIDEO)
        if (c.recording) RecordingIndicator(c.recordSeconds)
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Shutter(
                video = c.mode == CamMode.VIDEO,
                recording = c.recording,
                busy = c.nightBusy,
                onClick = onShutter,
            )
        }
        ModeBar(c)
    }

    if (showSettings) SettingsDialog(c) { showSettings = false }
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Record with sound?") },
            text = { Text("The microphone is used only to record sound together with your video. Audio never leaves your phone.") },
            confirmButton = { TextButton(onClick = { showAudioDialog = false; requestAudio() }) { Text("Allow microphone") } },
            dismissButton = { TextButton(onClick = { showAudioDialog = false; c.startRecording(false) }) { Text("No sound") } },
        )
    }
}
