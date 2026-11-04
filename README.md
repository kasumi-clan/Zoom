# Godly Smooth Zoom Camera

Ứng dụng camera Android (package `com.godly.camera`) viết bằng Kotlin, CameraX, Jetpack Compose, Material 3.
Mục tiêu: Samsung Galaxy A51, Android 13, ARM64 (minSdk 29, targetSdk 33).

> **Trạng thái:** mã nguồn v0.1 được viết nhưng **chưa được biên dịch hay chạy thử** trên máy thật.
> Lần build đầu tiên trên GitHub Actions có thể báo lỗi biên dịch — xem phần "Khắc phục sự cố".

## Tính năng đã có trong mã
- Xem trước trực tiếp, chụp ảnh (CameraX `ImageCapture`).
- Quay video thật (CameraX `VideoCapture`/`Recorder`), có đồng hồ REC; ghi âm chỉ sau khi xin quyền micro.
- Zoom điều khiển camera thật (`setZoomRatio`): nút preset 1x/2x/3x/5x/10x (video thêm 20x/30x nếu camera cho phép), thanh trượt, pinch-to-zoom, nội suy mượt. Giới hạn đúng mức tối đa camera báo cáo.
- Ứng dụng KHÔNG khẳng định đây là zoom quang học: Android không báo điều đó.
- Chế độ Night / Milky Way: chỉ phơi sáng dài khi camera hỗ trợ manual sensor (Camera2Interop); có đồng hồ đếm; nếu không hỗ trợ sẽ nói rõ.
- Chế độ Pro: ISO, tốc độ màn trập, cân bằng trắng, lấy nét tay, bù sáng, độ phân giải video, FPS. Mục không hỗ trợ bị khoá và ghi chú.
- Bộ lọc "làm nét nhẹ" (unsharp mask) tạo **bản sao** ảnh; không ghi đè ảnh gốc, không gửi ảnh đi đâu.
- Lưu bằng MediaStore: `Pictures/GodlyCamera/`, `Movies/GodlyCamera/`, tên file duy nhất.

## Chưa làm / chưa kiểm chứng
- Chưa có mô hình AI (khử nhiễu/deblur AI). Chỉ có làm nét nhẹ.
- Chưa có histogram, chưa có chạm-để-lấy-nét, chưa chọn độ phân giải ảnh chụp.
- 2K: chưa có (CameraX chỉ có 4K/1080p/720p/480p).
- 60 fps: chỉ hiện khi camera báo hỗ trợ, nhưng chưa chắc file quay ra đúng 60 fps — cần thử trên máy thật.
- Chưa kiểm tra vòng đời camera, ghi hình và Android 13 trên thiết bị thật.

## Cách build APK bằng GitHub Actions
1. Đưa toàn bộ thư mục này lên repo `kasumi-clan/GodlySmoothZoomCamera` (nhánh `main`), nhớ cả thư mục ẩn `.github`.
2. Vào tab **Actions** → workflow **Build Debug APK**. Nó tự chạy khi push lên `main`, hoặc bấm **Run workflow**.
3. Chờ chạy xong (dấu tích xanh).

## Tải APK
1. Mở lần chạy workflow vừa xong.
2. Kéo xuống phần **Artifacts**, tải `GodlyCamera-debug-apk` (file zip).
3. Giải nén được `app-debug.apk`, copy vào điện thoại và cài (bật "Cài ứng dụng không rõ nguồn gốc" nếu được hỏi).

Đường dẫn APK khi build: `app/build/outputs/apk/debug/app-debug.apk`.

## Gradle Wrapper
Tệp `gradle-wrapper.jar` là file nhị phân và chưa được đưa vào. Workflow tự chạy `gradle wrapper --gradle-version 8.7` nếu thiếu.
Trên máy có Gradle 8.7, bạn có thể tạo bằng: `gradle wrapper --gradle-version 8.7`.

## Khắc phục sự cố
- **Lỗi biên dịch Kotlin ở bước "Build debug APK":** mở log của bước đó, copy dòng `e: ...` và gửi lại để sửa.
- **Thiếu SDK/licence:** chạy lại workflow; bước `setup-android` sẽ chấp nhận licence.
- **Không thấy Artifact:** build đã lỗi; xem bước bị đỏ.
- **App không mở camera:** kiểm tra Cài đặt → Ứng dụng → Godly Camera → Quyền → Camera.
- **Không có tiếng trong video:** cấp quyền Micro, hoặc chọn "Allow microphone" khi app hỏi.
- **Mục Pro bị khoá:** camera đó không báo hỗ trợ điều khiển tay; đây là giới hạn phần cứng.

## Cấu trúc
`camera/` (CameraX, năng lực camera) · `zoom/` (ZoomEngine) · `processing/` (bộ làm nét, giao diện cho model sau này) · `storage/` (MediaStore) · `permissions/` · `ui/` (Compose).
