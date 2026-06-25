# Sửa lỗi lưu Edit Profile (Settings)

**Ngày:** 2026-06-26
**Phạm vi:** Backend (Spring Boot) + Frontend (React Native)
**Tính năng liên quan:** Trang Settings — Edit personal profile

## Bối cảnh & vấn đề

Bấm "Lưu hồ sơ" ở màn Edit Profile báo lỗi *"Không thể lưu, vui lòng kiểm tra kết nối backend"*. Truy vết `PUT /users/me/profile` (FE `SettingScreen.handleSaveProfile` → BE `UserService.updateProfile`) phát hiện **3 lỗi thật**:

1. **gender → 500.** FE gửi text tự do (`form.gender.trim()`). BE chạy `Gender.valueOf(gender.toUpperCase())` chỉ nhận `MALE/FEMALE/OTHER`. Giá trị rỗng (`""` vẫn non-null nên lọt qua `if (gender != null)`), placeholder "male/female/other", hay tiếng Việt → `IllegalArgumentException` → `GlobalExceptionHandler` fallback → **HTTP 500** với message lọt vào alert.
2. **birthDate → 400.** FE gửi ISO datetime (`new Date(form.birthDate).toISOString()` = `"2000-01-01T00:00:00.000Z"`). BE `UpdateProfileRequest.birthDate` là `Date` với `@JsonFormat(pattern="yyyy-MM-dd")` → Jackson không parse được → **400**.
3. **bloodType + conditions không bao giờ lưu.** BE `User`/`UserProfile` không có field nào cho chúng, và `UpdateProfileRequest` cũng không. FE đọc/ghi chúng qua `nextUser.healthMetrics` — một cấu trúc **BE không hề trả về**. Jackson bỏ qua field thừa → mất âm thầm.

## Mục tiêu

`PUT /users/me/profile` lưu bền vững mọi field của form (fullName, avatarUrl, gender, birthDate, heightCm, weightKg, bloodType, conditions), và `/auth/me` trả lại đúng để form hiển thị lại sau khi lưu. Không còn alert lỗi khi nhập hợp lệ.

## Phi mục tiêu (YAGNI)

- Không làm i18n / đổi ngôn ngữ (việc riêng, brainstorm sau).
- Không tạo cấu trúc `healthMetrics` mới ở BE — gom bloodType/conditions vào `UserProfile` sẵn có.
- Không đụng các trang Settings khác.

## Quyết định thiết kế (đã chốt)

- **bloodType + conditions:** thêm lưu trữ thật vào `UserProfile` (model BE), không tạo `healthMetrics`.
- **gender:** FE đổi thành picker MALE/FEMALE/OTHER; BE parse **an toàn** (rỗng/không khớp enum → bỏ qua, không ném).
- **birthDate:** FE gửi thẳng chuỗi `yyyy-MM-dd` (bỏ `.toISOString()`); `null` nếu rỗng.
- **DTO `birthday:Integer`** (khó hiểu, hiện không được set/map) → thay bằng `birthDate:String` dạng `yyyy-MM-dd` trong `UserProfileDto`, để FE round-trip qua `profile.birthDate`.

## Backend

### UserProfile (model) — `model/user/UserProfile.java`
Thêm 2 field:
```java
private String bloodType;          // optional
private java.util.List<String> conditions;  // optional
```
Giữ nguyên `birthday` (Date) hiện có.

### UpdateProfileRequest (DTO) — `dto/user/UpdateProfileRequest.java`
Thêm:
```java
private String bloodType;
private java.util.List<String> conditions;
```
Giữ `birthDate` (Date, `@JsonFormat yyyy-MM-dd`, `@Past`).

### UserService.updateProfile — `service/user/UserService.java`
- **gender an toàn:** thay
  ```java
  if (req.getGender() != null) { p.setGender(Gender.valueOf(req.getGender().toUpperCase())); }
  ```
  bằng parse an toàn: chỉ set khi giá trị không rỗng VÀ khớp enum; ngược lại bỏ qua (không ném). Tách helper `parseGender(String) : Gender` trả `null` nếu không hợp lệ.
- set `bloodType` khi `req.getBloodType() != null`.
- set `conditions` khi `req.getConditions() != null`.
- Giữ nguyên phần birthDate/height/weight/fullName/avatarUrl.

### UserProfileDto — `dto/user/UserProfileDto.java`
- Bỏ `private Integer birthday;`
- Thêm `private String birthDate;` (định dạng `yyyy-MM-dd`)
- Thêm `private String bloodType;`
- Thêm `private java.util.List<String> conditions;`

### UserResponseMapper — `dto/user/UserResponseMapper.java`
Trong `mapProfile`:
- map `birthDate`: nếu `p.getBirthday() != null` → format `Date` ra `"yyyy-MM-dd"` (dùng `SimpleDateFormat("yyyy-MM-dd")` hoặc chuyển qua `Instant`/`LocalDate` với zone UTC) → string; null nếu birthday null.
- map `bloodType` = `p.getBloodType()`.
- map `conditions` = `p.getConditions()`.

> Lưu ý: `UserService.updateProfile` đã trả qua `UserResponseMapper.toResponse(u)` (thống nhất từ feature trước), nên chỉ cần sửa mapper một chỗ.

## Frontend

### SettingScreen.tsx — `src/screens/AppScreen/Setting/SettingScreen.tsx`

**gender picker:** thay ô `TextInput` cho gender bằng 3 lựa chọn MALE/FEMALE/OTHER (dùng `TouchableOpacity` highlight lựa chọn đang chọn, lưu vào `form.gender`). Giá trị lưu là chuỗi viết hoa khớp enum (`MALE`/`FEMALE`/`OTHER`).

**payload `handleSaveProfile`:**
- `birthDate`: gửi `form.birthDate || null` (đã là `YYYY-MM-DD`), **bỏ** `new Date(form.birthDate).toISOString()`.
- giữ `bloodType`, `conditions` như cũ (giờ BE nhận).
- `gender`: gửi `form.gender || null` (picker đảm bảo hợp lệ; null nếu chưa chọn).

**syncUser + healthRows:** đọc từ `profile.*` thay vì `healthMetrics.*`:
- `heightCm: String(profile.heightCm || '')`, `weightKg: String(profile.weightKg || '')`
- `bloodType: profile.bloodType || ''`
- `conditions: (profile.conditions || []).join(', ')`
- `birthDate: (profile.birthDate || '').slice(0, 10)`
- `healthRows`: nhóm máu/chiều cao/cân nặng đọc từ `profile`.

Bỏ dần biến `metrics`/`healthMetrics` trong file (không còn nguồn dữ liệu).

### Type UserProfile (FE) — `src/components/Home/HeaderSection/types.ts`
Thêm vào `profile`:
```ts
bloodType?: string;
conditions?: string[];
```
(`birthDate` đã có trong type.)

## Xử lý lỗi

- BE: gender rỗng/không hợp lệ → bỏ qua, không ném (không còn 500). birthDate sai vẫn được `@JsonFormat`/`@Past` validate → 400 với message rõ (nhưng FE giờ gửi đúng format). User không tồn tại → `orElseThrow` như hiện có.
- FE: picker chặn gender vô nghĩa từ gốc. Lỗi mạng vẫn hiện alert như cũ.

## Testing

- **BE — `UserResponseMapperTest`** (bổ sung): map `bloodType`, `conditions`, `birthDate` (Date → "yyyy-MM-dd"); ca birthday null → birthDate null; ca conditions null.
- **BE — `UserServiceTest`** (bổ sung, Mockito): `updateProfile` với gender "MALE" → set MALE; gender "" → không set (không ném); gender "xyz" → không set; bloodType/conditions được lưu; trả response có các field đó.
- **FE:** lint + `tsc --noEmit` trên file đổi (không có test cho screen).

## Các file dự kiến chạm

**Backend (`DACN2_BEserver`):**
- `model/user/UserProfile.java` — thêm bloodType, conditions
- `dto/user/UpdateProfileRequest.java` — thêm bloodType, conditions
- `dto/user/UserProfileDto.java` — birthday→birthDate, thêm bloodType/conditions
- `service/user/UserService.java` — parseGender an toàn + set field mới
- `dto/user/UserResponseMapper.java` — map birthDate/bloodType/conditions
- test: `UserResponseMapperTest`, `UserServiceTest` (bổ sung)

**Frontend (`DACN2_FEserver`):**
- `src/screens/AppScreen/Setting/SettingScreen.tsx` — gender picker, payload, đọc từ profile
- `src/components/Home/HeaderSection/types.ts` — thêm bloodType/conditions vào profile
