# Làm mới UI/UX màn Đo nhịp tim

**Ngày:** 2026-06-26
**Phạm vi:** Frontend (React Native) — chỉ tầng trình bày màn `HeartMeasurementScreen`
**Tính năng liên quan:** Đo nhịp tim PPG (Spec 2 đã xong). Đây là spec UI/UX riêng, không đụng logic đo.

## Bối cảnh & vấn đề

Sau khi đo PPG hoạt động, màn `HeartMeasurementScreen` vẫn "thô sơ": camera preview chiếm khối lớn, icon tim tĩnh, waveform tĩnh (`HeartLineIcon`), chữ trạng thái nhỏ, layout rời rạc nền sáng. Người dùng từng bối rối khi chưa bắt được tín hiệu. Tham khảo các app đo nhịp tim hiện đại (Welltory, Instant Heart Rate): điểm mạnh là **hướng dẫn đặt tay bằng animation**, **BPM lớn realtime**, **tim đập + waveform sống**, **phản hồi chất lượng trực quan**, nền tối ấn tượng.

## Mục tiêu

Thiết kế lại phần trình bày của `HeartMeasurementScreen` cho đẹp + UX rõ ràng hơn: nền tối + xanh teal, tim đập theo nhịp thật, BPM lớn realtime, waveform sống, và trạng thái trực quan theo `ppg.quality`. **Không thay đổi** logic đo, native plugin, frame processor, analyzer, hay đường lưu.

## Quyết định thiết kế (đã chốt qua brainstorming)

- **Layout B** — trái tim trung tâm, BPM cực lớn ngay dưới.
- **Trạng thái kết hợp:** `no_finger` → hướng dẫn đặt tay (tim mờ + icon 👆 + chữ to + vòng nét đứt nhấp nháy); `weak`/`saturated` → phản hồi tiến triển (ô camera nhỏ + thanh độ-mạnh-tín-hiệu); `good` → tim đập + BPM.
- **Tim đập theo NHỊP THẬT:** chu kỳ = `60000 / bpm` ms, có halo glow mỗi nhịp.
- **Tông màu tối + xanh teal:** nền gradient `#0d1a18 → #0a0a0c`; accent teal `#2dd4bf` (sáng) / `#2D8C83` (theme app); badge chất lượng xanh-lá/vàng/cam.

## Phi mục tiêu (YAGNI)

- Không thêm dependency (gradient + svg đã có sẵn).
- Không đụng `ppgAnalyzer`, native plugin, frame processor, torch, hay finalize→HeartResult.
- Không đổi màn HeartResult / lịch sử.

## Ràng buộc kỹ thuật (đã xác minh)

- `react-native-linear-gradient@2.8.3` đã cài → dùng cho nền gradient.
- `react-native-svg@15.15.1` đã cài → dùng cho waveform sống.
- `Animated` (RN core) đã dùng; màn có sẵn `scaleAnim` với pulse loop **tốc độ cố định** (sẽ đổi thành BPM-driven).
- `analyze()` trả `PpgResult` gồm `bpm/quality/confidence` (+ debug fields). `ppgSamplesRef` là ref chứa mẫu `{t, red}`.

## Kiến trúc — tách thành component con (mỗi cái một việc)

`HeartMeasurementScreen.tsx` hiện đang lớn và trộn nhiều việc. Tách phần trình bày thành 3 component thuần (nhận props, không giữ logic đo):

### `PulsingHeart` (`src/screens/HeartMeasurement/components/PulsingHeart.tsx`)
- **Props:** `bpm: number | null`, `active: boolean`, `color: string`.
- **Việc:** khi `active && bpm` hợp lệ → chạy `Animated.loop` scale 1.0→1.15→1.0 với mỗi chu kỳ = `60000/bpm` ms, kèm halo (View tròn glow, opacity dao động đồng pha). Khi không active hoặc bpm null → tim tĩnh/mờ. Cập nhật chu kỳ khi `bpm` đổi (effect phụ thuộc `bpm`).
- **Phụ thuộc:** `Animated`, HeartIcon SVG.

### `LiveWaveform` (`src/screens/HeartMeasurement/components/LiveWaveform.tsx`)
- **Props:** `samples: number[]` (giá trị red/luma gần đây, vd 90 mẫu cuối ~3s), `color: string`.
- **Việc:** chuẩn hoá mẫu về [0,1] rồi vẽ `Svg` `Polyline` cuộn ngang. Rỗng → đường phẳng. Thuần trình bày.
- **Phụ thuộc:** `react-native-svg`.

### `QualityState` (`src/screens/HeartMeasurement/components/QualityState.tsx`)
- **Props:** `quality: PpgQuality`, `bpm: number | null`, `confidence: number`, `color: string`.
- **Việc:** render khu trung tâm theo quality:
  - `good` + bpm → `<PulsingHeart active bpm/>` + số BPM cực lớn.
  - `no_finger` → tim mờ + "👆" + "Đặt ngón tay che camera và đèn flash" + vòng nét đứt nhấp nháy.
  - `weak` → thanh độ-mạnh-tín-hiệu (suy từ `confidence` hoặc cố định mức thấp) + "Đang bắt tín hiệu… giữ yên tay".
  - `saturated` → "Ấn nhẹ tay hơn — tín hiệu quá sáng".
- **Phụ thuộc:** `PulsingHeart`.

### `HeartMeasurementScreen` (sửa)
- Điều phối: giữ NGUYÊN state machine đo (permission, torch, frame processor, interval `analyze`, finalize). Thay khối render giữa (progress ring + HeartIcon tĩnh + HeartLineIcon + bpmText) bằng: camera preview thu nhỏ (ô tròn ~74px) + `<QualityState/>` + `<LiveWaveform/>` + thanh tiến trình mảnh + hộp hướng dẫn gọn. Bọc nền bằng `LinearGradient` tối-teal.
- **Cấp dữ liệu cho waveform:** thêm một state nhẹ `waveform: number[]` cập nhật trong interval 1s sẵn có (lấy ~90 mẫu cuối từ `ppgSamplesRef`), truyền vào `LiveWaveform`. (Không tạo vòng lặp render mới — tái dùng interval đang có.)

## Bảng màu (hằng số dùng chung)
Khai báo trong màn (hoặc một file `theme` nhỏ của feature):
- `BG_TOP = '#0d1a18'`, `BG_BOTTOM = '#0a0a0c'`
- `ACCENT = '#2dd4bf'`, `ACCENT_DIM = '#2D8C83'`
- `QUALITY_GOOD = '#4ade80'`, `QUALITY_WEAK = '#facc15'`, `QUALITY_SATURATED = '#fb923c'`
- chữ chính `#ffffff`, phụ `#9ca3af`

## Xử lý lỗi & giữ nguyên

- Giữ: quyền camera (`getCameraErrorMessage`), torch (native Android / iOS prop), frame processor, `analyze` mỗi 1s, finalize chỉ khi `quality==='good' && bpm!=null` → `navigate('HeartResult', {bpm})`, retry message khi không tốt.
- Camera lỗi/không có đèn → vẫn hiển thị thông báo như cũ trong khu trung tâm.

## Testing

- Logic đo KHÔNG đổi → 13/13 test `ppgAnalyzer` giữ nguyên (chạy `npx jest src/screens/HeartMeasurement/ppgAnalyzer.test.ts`).
- 3 component con thuần trình bày: không có test tự động (animation/màu/layout cần mắt người) → verify bằng `tsc --noEmit` + `npm run lint` sạch trên file đổi + **xem trên thiết bị Android thật** (tim đập đúng nhịp, các trạng thái no_finger/weak/good hiển thị đúng, màu teal).

## Các file dự kiến chạm (Frontend — `DACN2_FEserver`)

- `src/screens/HeartMeasurement/components/PulsingHeart.tsx` *(mới)*
- `src/screens/HeartMeasurement/components/LiveWaveform.tsx` *(mới)*
- `src/screens/HeartMeasurement/components/QualityState.tsx` *(mới)*
- `src/screens/HeartMeasurement/HeartMeasurementScreen.tsx` — thay khối render giữa, bọc gradient, cấp `waveform` state; giữ nguyên logic đo. Đổi `scaleAnim` sang BPM-driven (chuyển vào `PulsingHeart`).
- styles trong màn (đổi sang tông tối-teal). Không thêm dependency.

## Phụ thuộc giữa các spec

- Cần Spec 2 (đo PPG) đã xong — spec này chỉ làm đẹp phần hiển thị của nó. Đã hoàn thành.
