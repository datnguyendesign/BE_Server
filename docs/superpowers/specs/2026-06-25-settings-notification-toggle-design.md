# Nối Notification toggle với backend (Settings)

**Ngày:** 2026-06-25
**Phạm vi:** Backend (Spring Boot) + Frontend (React Native)
**Tính năng liên quan:** Trang Settings — mục GENERAL

## Bối cảnh & vấn đề

Trang Settings ([SettingScreen.tsx](../../../src/screens/AppScreen/Setting/SettingScreen.tsx) ở repo FE) có 3 toggle trong mục GENERAL — Notification, Dark Mode, Data synchronization — nhưng cả ba chỉ là `useState` cục bộ: **không lưu lên server, mất trạng thái khi tắt app, không có tác dụng thật**.

Khảo sát backend cho thấy:
- `UserSettings.notifications.enabled` (mặc định `true`) đã tồn tại trong model, cùng DTO `UserSettingsDto` / `NotificationSettingsDto`.
- `AuthService.me()` (đứng sau `GET /auth/me`) **không map `profile`, `settings`, lẫn `goals`** vào `UserResponse` — dù `UserResponse` đã khai báo các field đó. FE không có cách đọc trạng thái notification hiện tại.
- `UserController` **không có endpoint cập nhật settings** — chỉ có `PUT /me/profile`.
- Dark Mode là việc thuần client (đổi theme toàn app) — app chưa có cơ chế theme sáng/tối.
- Data synchronization không có field tương ứng ở BE và ý nghĩa mơ hồ.

## Mục tiêu

Toggle **Notification** phản ánh và lưu trạng thái thật `notifications.enabled` của user. **Bỏ** toggle Dark Mode và Data synchronization khỏi UI.

## Phi mục tiêu (YAGNI)

- Không làm Dark Mode / ThemeContext / theme tối (dự án lớn riêng).
- Không làm Data synchronization.
- Không thêm 3 reminder con (uống nước/ngủ/tập luyện) — chỉ toggle bật/tắt tổng.
- Không sửa các trang con khác của Settings (Backup Email, Privacy, Language, Help...).

## Kiến trúc

Ba đơn vị tách bạch:

1. **`UserResponseMapper`** (BE, mới) — đơn vị thuần `User → UserResponse`, không I/O. Khử trùng lặp giữa `AuthService.me()` và `UserService` (cả hai đang/sẽ dựng `UserResponse`).
2. **Endpoint cập nhật notification** (BE) — `PATCH /users/me/settings/notifications`.
3. **Settings screen** (FE) — nối toggle thật, xóa 2 toggle thừa.

## Backend

### UserResponseMapper (mới)

`com.example.dacn2_beserver.dto.user.UserResponseMapper` (đặt cạnh DTO trong `dto/user` — repo không có package `mapper` riêng). Một static method:

```java
public static UserResponse toResponse(User u)
```

Map đầy đủ:
- `id, username, primaryEmail, status, roles, lastLoginAt, createdAt, updatedAt`
- `profile` → `UserProfileDto` (fullName, avatarUrl, gender→name(), heightCm, weightKg) — null nếu `u.getProfile()` null.
- `settings` → `UserSettingsDto` (unitSystem, language, timezone, notifications→`NotificationSettingsDto`(enabled, remindDrinkWater, remindSleep, remindWorkout)) — null nếu `u.getSettings()` null.
- `goals` → `UserGoalsDto` (dailySteps, dailyCaloriesIn, dailyCaloriesOut, dailyWaterMl, targetWeightKg) — null nếu `u.getGoals()` null.

`gender` map qua `.name()` chỉ khi khác null (giữ đúng logic hiện có trong `UserService.toResponse`).

### AuthService.me()

Thay phần dựng `UserResponse.builder()...` thủ công bằng `UserResponseMapper.toResponse(u)`. Giữ nguyên việc load user và ném `UserNotFoundException` khi không thấy.

### UserService

- Refactor `updateProfile` để trả `UserResponseMapper.toResponse(u)` thay cho `toResponse(u)` riêng; xóa method `toResponse` cũ (đã chuyển vào mapper).
- Thêm method:

```java
public UserResponse updateNotificationEnabled(String userId, boolean enabled)
```

Logic: load user (`orElseThrow`); nếu `settings == null` → khởi tạo `UserSettings.builder().build()`; nếu `settings.notifications == null` → khởi tạo `NotificationSettings.builder().build()`; set `enabled`; set `updatedAt = Instant.now()`; save; trả `UserResponseMapper.toResponse(u)`.

### UpdateNotificationSettingsRequest (mới)

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UpdateNotificationSettingsRequest {
    @NotNull
    private Boolean enabled;
}
```

### UserController

Thêm route:

```java
@PatchMapping("/me/settings/notifications")
public UserResponse updateNotificationSettings(
        @AuthenticationPrincipal AuthPrincipal principal,
        @Valid @RequestBody UpdateNotificationSettingsRequest req) {
    return userService.updateNotificationEnabled(principal.userId(), req.getEnabled());
}
```

`/users/**` đã yêu cầu JWT (không nằm trong permitAll) — không sửa SecurityConfig.

## Frontend

### SettingScreen.tsx

- **Xóa** state `darkMode`, `dataSync` và hai `<SettingRow>` Dark Mode + Data synchronization trong mục GENERAL. Mục GENERAL chỉ còn Notification.
- `syncUser()`: sau khi nhận `nextUser` từ `/auth/me`, đọc `nextUser.settings?.notifications?.enabled` (mặc định `true` nếu thiếu) và set vào state `notifications`.
- Toggle Notification — optimistic update:
  - Khi người dùng bật/tắt: cập nhật state `notifications` ngay; gọi `PATCH /users/me/settings/notifications` với body `{ enabled: nextValue }`.
  - Nếu request lỗi: revert state về giá trị cũ và `Alert.alert('Không thể cập nhật', ...)`.
  - Có thể chặn double-tap bằng một cờ `isSavingNotif` (giống `isSaving` cho profile).

### Type UserProfile

`src/components/Home/HeaderSection/types.ts` — thêm vào `UserProfile`:

```ts
settings?: {
  unitSystem?: string;
  language?: string;
  timezone?: string;
  notifications?: {
    enabled?: boolean;
    remindDrinkWater?: boolean;
    remindSleep?: boolean;
    remindWorkout?: boolean;
  };
} | null;
```

## Xử lý lỗi

- BE: user không tồn tại → `orElseThrow` (pattern hiện có). `settings`/`notifications` null → khởi tạo mặc định trước khi set (phòng dữ liệu cũ). `enabled` null trong body → 400 nhờ `@Valid` + `@NotNull`.
- FE: PATCH lỗi → revert toggle + Alert. Không chặn người dùng dùng tiếp.

## Testing

- **BE — `UserResponseMapperTest`** (thuần, không Spring): map đầy đủ profile/settings/goals; ca `profile == null`; ca `settings == null`; ca `goals == null`; gender null → field null (không NPE).
- **BE — `UserServiceTest`** (Mockito): `updateNotificationEnabled(true)` → user lưu với enabled=true; `(false)` → false; user có `settings.notifications == null` → khởi tạo rồi set, không NPE; user không tồn tại → ném.
- **FE**: lint + `tsc --noEmit` trên file đổi (project không có test cho screen này).

## Các file dự kiến chạm

**Backend (`DACN2_BEserver`):**
- `dto/user/UserResponseMapper.java` *(mới)*
- `dto/user/UpdateNotificationSettingsRequest.java` *(mới)*
- `service/auth/AuthService.java` — dùng mapper trong `me()`
- `service/user/UserService.java` — dùng mapper + `updateNotificationEnabled`
- `controller/UserController.java` — route PATCH mới
- test: `UserResponseMapperTest`, `UserServiceTest`

**Frontend (`DACN2_FEserver`):**
- `src/screens/AppScreen/Setting/SettingScreen.tsx` — nối toggle, xóa 2 toggle
- `src/components/Home/HeaderSection/types.ts` — thêm `settings` vào `UserProfile`
