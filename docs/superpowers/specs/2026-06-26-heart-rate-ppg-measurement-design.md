# Đo nhịp tim PPG thật qua camera (Spec 2 of 2)

**Ngày:** 2026-06-26
**Phạm vi:** Frontend (React Native) — gồm thêm native dependency + build lại app
**Tính năng liên quan:** Đo nhịp tim. Spec 1 (đường lưu trữ) đã xong; Spec 2 thay nguồn BPM giả lập bằng đo PPG thật.

## REVISION 2026-06-26 (sau khi triển khai Layer A + thử Layer B)

Trong lúc build, phát hiện plugin frame-processor bên thứ ba **`@systemic-games/vision-camera-rgb-averages@1.3.1` KHÔNG biên dịch được** với RN 0.82 `newArchEnabled=true` (native module thiếu `install(Promise)` của TurboModule spec — thư viện ra đời trước new architecture). VisionCamera 4.7.3 tự nó biên dịch native OK.

**Thay đổi đã chốt (thay phần "plugin có sẵn" trong Lớp B bên dưới):**
- **GỠ** dependency `@systemic-games/vision-camera-rgb-averages`.
- **TỰ VIẾT** một frame-processor plugin native dùng API chính thức của VisionCamera 4.7.3: subclass `com.mrousavy.camera.frameprocessors.FrameProcessorPlugin`, override `callback(Frame, Map): Object`, đăng ký qua `FrameProcessorPluginRegistry.addFrameProcessorPlugin("redAverage", initializer)` tại startup (trong `MainApplication.onCreate`).
- **Tín hiệu = trung bình mặt phẳng LUMA (Y)** của `frame.getImage().getPlanes()[0]` (YUV_420_888), lấy mẫu thưa (mỗi ~16–32 px) cho hot-path nhanh. Dưới ngón tay + đèn flash, độ sáng Y dao động theo mạch — proxy PPG chuẩn, rẻ hơn decode RGB. `ppgAnalyzer.analyze()` (Lớp A) chỉ cần chuỗi giá trị sáng 0–255; nguồn Y hay red đều hợp lệ; quality gate 200/253 vẫn áp dụng.
- Phía JS: `VisionCameraProxy.initFrameProcessorPlugin('redAverage', {})` rồi `plugin.call(frame)` trong `useFrameProcessor`; đẩy về JS bằng `Worklets.createRunOnJS` (worklets-core, KHÔNG reanimated).
- **Giữ nguyên** (đã đúng + new-arch OK): `react-native-worklets-core`, babel plugin, Android torch native module, `torch.ts`, và override NDK trong `android/build.gradle` (ép `ndkVersion=27.1.12297006` cho mọi native module).

Các phần khác của spec (Lớp A thuật toán, Lớp C UI + xoá giả lập, quality gate, testing) **không đổi**. Mục "Lớp B — plugin RGB có sẵn" bên dưới được thay bằng plugin tự viết như trên.

> Ghi chú thuật toán (Lớp A đã triển khai): dùng **detrend + quét tần số DFT trực tiếp** trong dải 0.65–4Hz (thuần JS, không cần Butterworth+fft.js) — đơn giản hơn, không dependency, test đầy đủ. (Đã duyệt ở bước viết plan.)

## Bối cảnh & vấn đề

`HeartMeasurementScreen` hiện hiển thị camera + đèn flash nhưng **không xử lý frame**; BPM sinh từ `Math.random()` (68–90) + sóng sin giả + nhiễu (dòng ~107, 194–209). Spec 1 đã làm đường lưu: màn kết quả POST BPM lên `/health/heart-rate`, cộng vào DailyAggregate, hiện Home/Calendar, có màn lịch sử. Spec 2 thay nguồn BPM giả bằng **PPG thật**: đặt ngón tay che camera sau + đèn flash → đọc trung bình kênh đỏ mỗi frame → xử lý tín hiệu → BPM thật.

## Mục tiêu

Đo BPM thật bằng PPG camera, có kiểm soát chất lượng (phát hiện không đặt ngón tay / tín hiệu yếu), rồi chốt BPM và lưu qua đường đã có ở Spec 1.

## Phi mục tiêu (YAGNI)

- Không làm biểu đồ xu hướng / thống kê lịch sử nâng cao (tách spec FE riêng sau — độc lập, không phụ thuộc native).
- Không thêm `react-native-reanimated` (chỉ cần cho Skia; reanimated v4 còn xung đột build Android với worklets-core).
- Không đụng backend (Spec 1 đã đủ đường lưu).
- Không hỗ trợ wearable/Health Connect (hướng khác).

## Ràng buộc khả thi (từ nghiên cứu, định hình thiết kế)

1. **Cần native dep + build lại app** (không phải JS thuần): frame processor của vision-camera v4 bắt buộc `react-native-worklets-core`.
2. **Có plugin RGB v4 sẵn** — không cần viết native plugin đọc pixel: `@systemic-games/vision-camera-rgb-averages` v1.3.1 (test với vision-camera ~4.6.1 + worklets-core ^1.3.3), trả `{redAverage, greenAverage, blueAverage}` (0–255) mỗi frame.
3. **Bug Android: torch + frame processor.** Trên Android v4, `torch='on'` bị vô hiệu khi có frame processor đăng ký (GitHub #2838/#3045, "not planned"). PPG cần đèn → **dùng native module nhỏ gọi `CameraManager.setTorchMode()`** song song frame processor. iOS không bị (dùng `torch` prop).

## Kiến trúc — 3 lớp tách bạch (thứ tự rủi ro tăng dần)

### Lớp A — Thuật toán PPG thuần (TypeScript, KHÔNG cần build, test đầy đủ bằng Jest)

Module `src/screens/HeartMeasurement/ppgAnalyzer.ts`, không phụ thuộc RN/native.

- **Đầu vào:** `samples: { t: number /*ms*/, red: number /*0–255*/ }[]`, `sampleRateHz: number`.
- **Đầu ra:** `{ bpm: number | null, quality: 'good' | 'weak' | 'no_finger' | 'saturated', confidence: number /*0–1*/ }`.
- **Pipeline (theo chuẩn camera PPG):**
  1. **Quality gate:** `redMean < 200` → `no_finger`; `redMean > 253` → `saturated`; biên độ AC/DC `< 0.5%` → `weak`. Các ca này trả `bpm = null`.
  2. **Detrend:** trừ baseline moving-average (cửa sổ ~30–60 mẫu).
  3. **Bandpass:** Butterworth bậc 4, dải `0.65–4.0 Hz` (≈42–240 BPM). Hệ số SOS **hardcode** (tính sẵn bằng SciPy ở thời điểm viết plan), không cần native.
  4. **FFT:** tần số trội qua `fft.js` (npm). `bpm = round(dominantFreqHz × 60)`.
  5. **Validate:** BPM ngoài `40–200` → loại (`bpm = null`, quality `weak`).
- **Cửa sổ:** 10–15s @ ~30fps (300–450 mẫu); bỏ 4–5s đầu cho filter settle.
- **Hàm con tách riêng** (mỗi cái test được): `assessQuality(samples)`, `detrend(series, window)`, `bandpass(series, sosCoeffs)`, `dominantFrequencyHz(series, sampleRateHz)`.
- **confidence:** suy ra từ độ nhọn của đỉnh phổ (tỷ lệ năng lượng đỉnh / tổng) — chuẩn hoá 0–1.

### Lớp B — Tích hợp native (build lại app, verify thủ công)

- **Dependencies:** thêm `react-native-worklets-core` (~1.6.x) + `@systemic-games/vision-camera-rgb-averages` (^1.3.1). KHÔNG thêm reanimated.
- **babel.config.js:** thêm plugin `['react-native-worklets-core/plugin']` (đặt cuối danh sách plugins nếu thứ tự yêu cầu).
- **Frame processor** trong `HeartMeasurementScreen`: `useFrameProcessor` chạy plugin RGB → `redAverage`; đẩy mẫu `{ t, red }` về JS (qua cơ chế của plugin / `runOnJS`) gom vào ref buffer. Mỗi ~1s chạy `ppgAnalyzer` trên cửa sổ hiện tại → cập nhật BPM realtime + chất lượng.
- **Native torch module (Android):** module Kotlin nhỏ `TorchModule` expose `setTorch(boolean)` gọi `CameraManager.setTorchMode(cameraId, on)`. Helper JS `setTorch(on)` thống nhất: Android dùng native module; iOS set `torch` prop. Guard `device?.hasTorch`.
- **Đơn vị:** native torch module độc lập; frame-processor wiring tách khỏi thuật toán (Lớp A); màn hình chỉ điều phối (thu mẫu → gọi analyzer → cập nhật state).

### Lớp C — UI đo thật + kiểm soát chất lượng

- `HeartMeasurementScreen`: **xóa toàn bộ giả lập** (`simulatedBpmRef`, `Math.random`, sóng sin — dòng ~107, 194–209) và logic `estimateBpmFromPulseSamples` cũ. Thay bằng dữ liệu thật từ frame processor + `ppgAnalyzer`.
- Realtime UI: vòng tiến trình theo thời lượng đo; trạng thái chất lượng ("Đặt ngón tay che camera và đèn flash" khi `no_finger`; "Giữ yên, tín hiệu yếu" khi `weak`); BPM tạm khi `good`.
- Khi đủ cửa sổ ổn định + chất lượng `good` → chốt BPM, điều hướng sang `HeartResult` (màn này đã lưu qua Spec 1). Nếu hết thời gian mà chất lượng kém suốt → không chốt, nhắc đặt lại ngón tay, KHÔNG lưu rác.

## Xử lý lỗi

- Không có camera/đèn flash → thông báo rõ (đã có `getCameraErrorMessage`); guard `device?.hasTorch`.
- Chất lượng kém suốt phiên → không chốt BPM, không POST; hướng dẫn người dùng.
- Frame processor / plugin lỗi khi chạy → bắt lỗi, thông báo, không crash.
- Android torch module lỗi (cameraId null, quyền) → vẫn cho đo bằng ánh sáng môi trường + cảnh báo chất lượng có thể thấp.

## Testing

- **Lớp A (chính):** Jest đầy đủ, đầu vào là mảng số tổng hợp — KHÔNG cần thiết bị:
  - sin 1.2 Hz (72 BPM) sạch → `bpm ≈ 72`, quality `good`.
  - sin 1.0 Hz (60 BPM), 1.5 Hz (90 BPM) → BPM khớp trong sai số bin (~±6 BPM @10s).
  - chuỗi phẳng / redMean thấp → `no_finger`.
  - redMean > 253 → `saturated`.
  - biên độ rất nhỏ → `weak`.
  - tần số ngoài dải (BPM > 200) → loại.
  - các hàm con (`detrend`, `bandpass`, `dominantFrequencyHz`) test riêng với tín hiệu biết trước.
- **Lớp B/C (không test tự động được — cần thiết bị thật):** **verify thủ công trên Android thật** (qua USB+adb): đặt ngón tay che camera + đèn → BPM hợp lý lúc nghỉ (~60–90), so sánh với một app/thiết bị đo khác; bỏ ngón tay → báo `no_finger`; đèn flash bật được khi frame processor chạy (kiểm chứng native torch module).

## Thứ tự thực thi

1. **Lớp A** — thuật toán + Jest (không build). Phần khó nhất, verify trước.
2. **Lớp B** — deps + babel + native torch module + frame processor wiring. Build lại, verify thủ công.
3. **Lớp C** — UI đo thật + kiểm soát chất lượng; xóa giả lập.

## Các file dự kiến chạm (Frontend — `DACN2_FEserver`)

- `src/screens/HeartMeasurement/ppgAnalyzer.ts` *(mới)* — thuật toán thuần.
- `src/screens/HeartMeasurement/ppgAnalyzer.test.ts` *(mới)* — Jest.
- `package.json` — thêm `react-native-worklets-core`, `@systemic-games/vision-camera-rgb-averages`, `fft.js`.
- `babel.config.js` — thêm worklets-core plugin.
- `src/screens/HeartMeasurement/torch.ts` *(mới)* — helper setTorch (Android native module + iOS prop).
- Native: `android/.../TorchModule.kt` + package registration *(mới)*.
- `src/screens/HeartMeasurement/HeartMeasurementScreen.tsx` — frame processor, xóa giả lập, UI chất lượng.

## Phụ thuộc giữa các spec

- **Cần Spec 1 đã xong** (đường lưu `/health/heart-rate`): màn kết quả lưu BPM thật qua đó. Đã hoàn thành.
- **Biểu đồ lịch sử nâng cao**: tách spec FE riêng sau, không phụ thuộc Spec 2.
