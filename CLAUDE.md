# CLAUDE.md
Project: Godly Smooth Zoom Camera (`com.godly.camera`), Android, Kotlin, CameraX, Compose, AGP 8.5.2 / Gradle 8.7 / JDK 17.

Rules for any future work:
- Never claim a build passed without real Gradle/Actions logs.
- Only offer camera features the device reports (see `camera/CameraCaps.kt`). Disable and label unsupported controls.
- Zoom must go through `ZoomEngine` -> `CameraControl.setZoomRatio`. Never fake zoom in UI. Do not call zoom "optical".
- Never overwrite original media; save via `storage/MediaStoreSaver.kt`.
- No cloud upload of photos, no automatic model downloads, no unneeded permissions.
- Keep UI light for Galaxy A51: no real blur.
- Build command: `./gradlew assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
