# Settings Notification Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Settings "Notification" toggle persist to the backend via a new `PATCH /users/me/settings/notifications` endpoint, complete `AuthService.me()` so it returns `profile`/`settings`/`goals` through a shared `UserResponseMapper`, and remove the unwired Dark Mode / Data synchronization toggles.

**Architecture:** A pure `UserResponseMapper.toResponse(User)` centralizes `User → UserResponse` mapping so `/auth/me` and `/users/me/profile` return identical shapes. `UserService` gains an `updateNotificationEnabled` method behind a new `PATCH` route. The React Native Settings screen reads `settings.notifications.enabled` from `/auth/me` and saves changes optimistically (revert + alert on failure).

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data MongoDB, JUnit 5 + Mockito (`spring-boot-starter-test`), Maven wrapper. Frontend: React Native 0.82 / TypeScript, Axios.

## Global Constraints

- Backend routes have **no `/api` prefix**. New route is `PATCH /users/me/settings/notifications`.
- `/users/**` already requires JWT (not in `SecurityConfig` permitAll) — do NOT modify SecurityConfig.
- `notifications.enabled` defaults to `true` (model `@Builder.Default`); FE defaults the toggle to `true` when the field is absent.
- Scope is the single on/off Notification toggle only. Do NOT add the 3 sub-reminders (water/sleep/workout), Dark Mode, Data sync, or touch other Settings sub-pages.
- `User.getSettings()` / `getGoals()` are never null on fresh users (`@Builder.Default`), but legacy documents may have null `settings` or null `settings.notifications` — initialize defaults before mutating.
- Tests must NOT use `@SpringBootTest` (no live MongoDB/Redis). Pure unit test for the mapper; Mockito for the service.
- Running backend tests on this machine requires the documented Avast-truststore + cached Maven 3.9.11 workaround (see below).

### Backend test command (this machine only)

```
cd /d/DATN/DACN2_BEserver
export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.17.10-hotspot"
export MAVEN_OPTS="-Djavax.net.ssl.trustStore=C:\\Users\\ASUS\\AppData\\Local\\Temp\\cacerts-avast -Djavax.net.ssl.trustStorePassword=changeit"
MVN="/c/Users/ASUS/.m2/wrapper/dists/apache-maven-3.9.11/03d7e36a140982eea48e22c1dcac01d8862b2550b2939e09a0809bbc5182a5bc/bin/mvn"
"$MVN" test -Dtest=SomeTestClass
```

Use only focused `-Dtest=...`; never the full suite (`@SpringBootTest` needs a live DB).

---

## File Structure

**Backend (`DACN2_BEserver`):**
- Create `src/main/java/com/example/dacn2_beserver/dto/user/UserResponseMapper.java` — pure `User → UserResponse`.
- Create `src/main/java/com/example/dacn2_beserver/dto/user/UpdateNotificationSettingsRequest.java` — request body DTO.
- Modify `src/main/java/com/example/dacn2_beserver/service/auth/AuthService.java` — `me()` uses the mapper.
- Modify `src/main/java/com/example/dacn2_beserver/service/user/UserService.java` — use mapper + add `updateNotificationEnabled`.
- Modify `src/main/java/com/example/dacn2_beserver/controller/UserController.java` — new PATCH route.
- Create `src/test/java/com/example/dacn2_beserver/dto/user/UserResponseMapperTest.java`.
- Create `src/test/java/com/example/dacn2_beserver/service/user/UserServiceTest.java`.

**Frontend (`DACN2_FEserver`):**
- Modify `src/components/Home/HeaderSection/types.ts` — add `settings` to `UserProfile`.
- Modify `src/screens/AppScreen/Setting/SettingScreen.tsx` — wire toggle, remove 2 toggles.

---

## Task 1: UserResponseMapper (pure mapping unit)

**Files:**
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/user/UserResponseMapper.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/dto/user/UserResponseMapperTest.java`

**Interfaces:**
- Consumes (existing): `User` getters (`getId/getUsername/getPrimaryEmail/getStatus/getRoles/getLastLoginAt/getCreatedAt/getUpdatedAt/getProfile/getSettings/getGoals`); `UserProfile` (`getFullName/getAvatarUrl/getGender/getHeightCm/getWeightKg`, `getGender()` returns enum with `.name()`); `UserSettings` (`getUnitSystem/getLanguage/getTimezone/getNotifications`); `NotificationSettings` (`isEnabled/isRemindDrinkWater/isRemindSleep/isRemindWorkout`); `UserGoals` (`getDailySteps/getDailyCaloriesIn/getDailyCaloriesOut/getDailyWaterMl/getTargetWeightKg`).
- Produces: `static UserResponse toResponse(User u)` — used by Tasks 2 and 3.

- [ ] **Step 1: Write the failing test**

Create `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/dto/user/UserResponseMapperTest.java`:

```java
package com.example.dacn2_beserver.dto.user;

import com.example.dacn2_beserver.model.enums.Gender;
import com.example.dacn2_beserver.model.enums.UnitSystem;
import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserGoals;
import com.example.dacn2_beserver.model.user.UserProfile;
import com.example.dacn2_beserver.model.user.UserSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserResponseMapperTest {

    @Test
    void mapsFullUser() {
        User u = User.builder()
                .id("u1").username("dat").primaryEmail("dat@example.com")
                .profile(UserProfile.builder()
                        .fullName("Dat Nguyen").avatarUrl("https://a/x.png")
                        .gender(Gender.MALE).heightCm(175.0).weightKg(68.0).build())
                .settings(UserSettings.builder()
                        .unitSystem(UnitSystem.METRIC).language("vi").timezone("Asia/Ho_Chi_Minh")
                        .notifications(NotificationSettings.builder()
                                .enabled(false).remindDrinkWater(true)
                                .remindSleep(true).remindWorkout(false).build())
                        .build())
                .goals(UserGoals.builder()
                        .dailySteps(9000).dailyCaloriesIn(2000).dailyCaloriesOut(500)
                        .dailyWaterMl(2000).targetWeightKg(65.0).build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getId()).isEqualTo("u1");
        assertThat(r.getProfile().getFullName()).isEqualTo("Dat Nguyen");
        assertThat(r.getProfile().getGender()).isEqualTo("MALE");
        assertThat(r.getSettings().getLanguage()).isEqualTo("vi");
        assertThat(r.getSettings().getNotifications().getEnabled()).isFalse();
        assertThat(r.getGoals().getDailySteps()).isEqualTo(9000);
    }

    @Test
    void nullProfileSettingsGoalsDoNotThrow() {
        User u = User.builder().id("u2").username("x").build();
        u.setProfile(null);
        u.setSettings(null);
        u.setGoals(null);

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getId()).isEqualTo("u2");
        assertThat(r.getProfile()).isNull();
        assertThat(r.getSettings()).isNull();
        assertThat(r.getGoals()).isNull();
    }

    @Test
    void nullGenderMapsToNull() {
        User u = User.builder().id("u3").username("y")
                .profile(UserProfile.builder().fullName("No Gender").build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getProfile().getGender()).isNull();
        assertThat(r.getProfile().getFullName()).isEqualTo("No Gender");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (use the Backend test command): `"$MVN" test -Dtest=UserResponseMapperTest`
Expected: FAIL — compilation error, `UserResponseMapper` does not exist.

- [ ] **Step 3: Write the implementation**

Create `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/user/UserResponseMapper.java`:

```java
package com.example.dacn2_beserver.dto.user;

import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserGoals;
import com.example.dacn2_beserver.model.user.UserProfile;
import com.example.dacn2_beserver.model.user.UserSettings;

/** Ánh xạ thuần User -> UserResponse, dùng chung cho /auth/me và /users/me/*. */
public final class UserResponseMapper {

    private UserResponseMapper() {
    }

    public static UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .primaryEmail(u.getPrimaryEmail())
                .profile(mapProfile(u.getProfile()))
                .settings(mapSettings(u.getSettings()))
                .goals(mapGoals(u.getGoals()))
                .status(u.getStatus())
                .roles(u.getRoles())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private static UserProfileDto mapProfile(UserProfile p) {
        if (p == null) {
            return null;
        }
        return UserProfileDto.builder()
                .fullName(p.getFullName())
                .avatarUrl(p.getAvatarUrl())
                .gender(p.getGender() == null ? null : p.getGender().name())
                .heightCm(p.getHeightCm())
                .weightKg(p.getWeightKg())
                .build();
    }

    private static UserSettingsDto mapSettings(UserSettings s) {
        if (s == null) {
            return null;
        }
        return UserSettingsDto.builder()
                .unitSystem(s.getUnitSystem())
                .language(s.getLanguage())
                .timezone(s.getTimezone())
                .notifications(mapNotifications(s.getNotifications()))
                .build();
    }

    private static NotificationSettingsDto mapNotifications(NotificationSettings n) {
        if (n == null) {
            return null;
        }
        return NotificationSettingsDto.builder()
                .enabled(n.isEnabled())
                .remindDrinkWater(n.isRemindDrinkWater())
                .remindSleep(n.isRemindSleep())
                .remindWorkout(n.isRemindWorkout())
                .build();
    }

    private static UserGoalsDto mapGoals(UserGoals g) {
        if (g == null) {
            return null;
        }
        return UserGoalsDto.builder()
                .dailySteps(g.getDailySteps())
                .dailyCaloriesIn(g.getDailyCaloriesIn())
                .dailyCaloriesOut(g.getDailyCaloriesOut())
                .dailyWaterMl(g.getDailyWaterMl())
                .targetWeightKg(g.getTargetWeightKg())
                .build();
    }
}
```

Note: `NotificationSettings` uses primitive `boolean` getters (`isEnabled()` etc.); `NotificationSettingsDto` fields are `Boolean` — autoboxing handles this. `UserProfileDto.birthday` (Integer) is intentionally left unset, matching the existing `UserService.toResponse` behavior.

- [ ] **Step 4: Run test to verify it passes**

Run: `"$MVN" test -Dtest=UserResponseMapperTest`
Expected: PASS — 3 tests green.

- [ ] **Step 5: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/dto/user/UserResponseMapper.java \
        src/test/java/com/example/dacn2_beserver/dto/user/UserResponseMapperTest.java
git commit -m "feat(user): add shared UserResponseMapper for full user mapping"
```

---

## Task 2: AuthService.me() uses the mapper

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/auth/AuthService.java`

**Interfaces:**
- Consumes: `UserResponseMapper.toResponse(User)` (Task 1).
- Produces: unchanged HTTP contract `GET /auth/me` → `UserResponse`, now including `profile`/`settings`/`goals`.

- [ ] **Step 1: Edit `me()`**

In `AuthService.java`, the `me` method currently builds the response inline (id/username/primaryEmail/status/roles/lastLoginAt/createdAt/updatedAt only). Replace its `return UserResponse.builder()...build();` with the mapper call. The method becomes:

```java
        public UserResponse me(AuthPrincipal principal) {
            User u = userRepository.findById(principal.userId()).orElseThrow(() -> new UserNotFoundException(principal.getName()));
            return UserResponseMapper.toResponse(u);
        }
```

Add the import `com.example.dacn2_beserver.dto.user.UserResponseMapper;` (the file already imports `com.example.dacn2_beserver.dto.user.UserResponse`).

- [ ] **Step 2: Compile to verify**

Run: `"$MVN" -o test-compile`
Expected: BUILD SUCCESS (no test run needed; this is a refactor verified by Task 1's mapper test + compilation). If `-o` fails for missing artifacts, drop `-o`.

- [ ] **Step 3: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/auth/AuthService.java
git commit -m "fix(auth): return profile/settings/goals from /auth/me via mapper"
```

---

## Task 3: Notification settings endpoint

**Files:**
- Create: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/user/UpdateNotificationSettingsRequest.java`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/user/UserService.java`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/controller/UserController.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/user/UserServiceTest.java`

**Interfaces:**
- Consumes: `UserResponseMapper.toResponse(User)` (Task 1); `UserRepository.findById/save`; `User.getSettings/setSettings`; `UserSettings.getNotifications/setNotifications`; `NotificationSettings.setEnabled(boolean)`; `AuthPrincipal.userId()`.
- Produces: `UserService.updateNotificationEnabled(String userId, boolean enabled) : UserResponse`; route `PATCH /users/me/settings/notifications`.

- [ ] **Step 1: Write the failing test**

Create `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/user/UserServiceTest.java`:

```java
package com.example.dacn2_beserver.service.user;

import com.example.dacn2_beserver.dto.user.UserResponse;
import com.example.dacn2_beserver.model.user.NotificationSettings;
import com.example.dacn2_beserver.model.user.User;
import com.example.dacn2_beserver.model.user.UserSettings;
import com.example.dacn2_beserver.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    private User userWithEnabled(boolean enabled) {
        return User.builder().id("u1").username("dat")
                .settings(UserSettings.builder()
                        .notifications(NotificationSettings.builder().enabled(enabled).build())
                        .build())
                .build();
    }

    @Test
    void enableNotification() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithEnabled(false)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", true);

        assertThat(res.getSettings().getNotifications().getEnabled()).isTrue();
    }

    @Test
    void disableNotification() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithEnabled(true)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", false);

        assertThat(res.getSettings().getNotifications().getEnabled()).isFalse();
    }

    @Test
    void initializesNotificationsWhenNull() {
        User legacy = User.builder().id("u1").username("dat")
                .settings(UserSettings.builder().notifications(null).build())
                .build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(legacy));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", true);

        assertThat(res.getSettings().getNotifications().getEnabled()).isTrue();
    }

    @Test
    void initializesSettingsWhenNull() {
        User legacy = User.builder().id("u1").username("dat").build();
        legacy.setSettings(null);
        when(userRepository.findById("u1")).thenReturn(Optional.of(legacy));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse res = userService.updateNotificationEnabled("u1", true);

        assertThat(res.getSettings().getNotifications().getEnabled()).isTrue();
    }

    @Test
    void unknownUserThrows() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.updateNotificationEnabled("ghost", true))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void savesPersistedUser() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(userWithEnabled(false)));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateNotificationEnabled("u1", true);

        assertThat(captor.getValue().getSettings().getNotifications().isEnabled()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$MVN" test -Dtest=UserServiceTest`
Expected: FAIL — `updateNotificationEnabled` does not exist (compile error).

- [ ] **Step 3: Create the request DTO**

Create `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/user/UpdateNotificationSettingsRequest.java`:

```java
package com.example.dacn2_beserver.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationSettingsRequest {
    @NotNull
    private Boolean enabled;
}
```

- [ ] **Step 4: Add the service method and use the mapper**

In `UserService.java`:

1. Replace the body of `updateProfile`'s final `return toResponse(u);` with `return UserResponseMapper.toResponse(u);`.
2. Delete the private `toResponse(User u)` method (now provided by the mapper).
3. Add import `com.example.dacn2_beserver.dto.user.UserResponseMapper;` and `com.example.dacn2_beserver.model.user.NotificationSettings;` and `com.example.dacn2_beserver.model.user.UserSettings;`.
4. Add the new method:

```java
    public UserResponse updateNotificationEnabled(String userId, boolean enabled) {
        User u = userRepository.findById(userId).orElseThrow();

        UserSettings settings = u.getSettings() == null ? UserSettings.builder().build() : u.getSettings();
        NotificationSettings notifications = settings.getNotifications() == null
                ? NotificationSettings.builder().build()
                : settings.getNotifications();

        notifications.setEnabled(enabled);
        settings.setNotifications(notifications);
        u.setSettings(settings);
        u.setUpdatedAt(Instant.now());
        u = userRepository.save(u);

        return UserResponseMapper.toResponse(u);
    }
```

(`Instant` is already imported in `UserService`.)

- [ ] **Step 5: Add the controller route**

In `UserController.java`, add imports `com.example.dacn2_beserver.dto.user.UpdateNotificationSettingsRequest;` and `org.springframework.web.bind.annotation.PatchMapping;`, then add the method:

```java
    @PatchMapping("/me/settings/notifications")
    public UserResponse updateNotificationSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateNotificationSettingsRequest req
    ) {
        return userService.updateNotificationEnabled(principal.userId(), req.getEnabled());
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run: `"$MVN" test -Dtest=UserServiceTest`
Expected: PASS — 6 tests green.

- [ ] **Step 7: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/dto/user/UpdateNotificationSettingsRequest.java \
        src/main/java/com/example/dacn2_beserver/service/user/UserService.java \
        src/main/java/com/example/dacn2_beserver/controller/UserController.java \
        src/test/java/com/example/dacn2_beserver/service/user/UserServiceTest.java
git commit -m "feat(user): add PATCH /users/me/settings/notifications endpoint"
```

---

## Task 4: Frontend — wire toggle, remove dead toggles

**Files:**
- Modify: `DACN2_FEserver/src/components/Home/HeaderSection/types.ts`
- Modify: `DACN2_FEserver/src/screens/AppScreen/Setting/SettingScreen.tsx`

**Interfaces:**
- Consumes: backend `PATCH /users/me/settings/notifications` (Task 3); `/auth/me` now returns `settings.notifications.enabled` (Task 2).
- Produces: none (leaf UI change).

- [ ] **Step 1: Add `settings` to the UserProfile type**

In `src/components/Home/HeaderSection/types.ts`, add a `settings` field to the `UserProfile` type, after the `healthMetrics` block (before the closing `};` of the type):

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

- [ ] **Step 2: Remove the Dark Mode and Data sync state**

In `src/screens/AppScreen/Setting/SettingScreen.tsx`, remove these two state declarations (currently lines ~61-62):

```tsx
  const [darkMode, setDarkMode] = useState(true);
  const [dataSync, setDataSync] = useState(true);
```

Keep `const [notifications, setNotifications] = useState(true);`. Add a saving guard right after it:

```tsx
  const [isSavingNotif, setIsSavingNotif] = useState(false);
```

- [ ] **Step 3: Read the real notification state in syncUser**

In `syncUser`, after `setUser(nextUser);` (around line 84), add:

```tsx
      setNotifications(nextUser.settings?.notifications?.enabled ?? true);
```

- [ ] **Step 4: Add the optimistic toggle handler**

Add this handler inside the component (e.g. just after `handleSaveProfile`):

```tsx
  const handleToggleNotifications = async () => {
    if (isSavingNotif) return;
    const next = !notifications;
    setNotifications(next); // optimistic
    setIsSavingNotif(true);
    try {
      await api.patch('/users/me/settings/notifications', { enabled: next });
    } catch (error: any) {
      setNotifications(!next); // revert
      Alert.alert(
        'Không thể cập nhật',
        error?.response?.data?.message || 'Vui lòng kiểm tra kết nối backend.',
      );
    } finally {
      setIsSavingNotif(false);
    }
  };
```

- [ ] **Step 5: Update the GENERAL section JSX**

Replace the entire GENERAL `<SettingsSection>` block (the one containing Notification, Dark Mode, Data synchronization) with a single Notification row wired to the handler:

```tsx
            <SettingsSection title="GENERAL">
              <SettingRow
                IconComponent={BellIcon}
                color={theme.colors.orange}
                iconColor={theme.colors.white}
                title="Notification"
                type="toggle"
                toggleState={notifications}
                onToggle={handleToggleNotifications}
              />
            </SettingsSection>
```

The now-unused icon imports `MoonIcon` and `CloudIcon` should be removed from the import block at the top of the file to keep lint clean (they were only used by the removed rows).

- [ ] **Step 6: Lint and type-check**

Run:
```
cd /d/DATN/DACN2_FEserver
npm run lint 2>&1 | tail -20
npx --no-install tsc --noEmit 2>&1 | grep -E "SettingScreen|HeaderSection/types" || echo "no type errors in changed files"
```
Expected: no new ESLint errors in the two changed files; no tsc errors referencing the changed files. (Pre-existing warnings/test-file tsc errors elsewhere are unrelated — ignore.)

- [ ] **Step 7: Commit**

```bash
cd /d/DATN/DACN2_FEserver
git add src/components/Home/HeaderSection/types.ts \
        src/screens/AppScreen/Setting/SettingScreen.tsx
git commit -m "feat(settings): persist notification toggle, remove dead toggles"
```

---

## Manual verification (after all tasks)

1. Start backend (`./mvnw spring-boot:run` on a network without TLS interception).
2. `GET /auth/me` with a valid Bearer token → response now includes `profile`, `settings` (with `notifications.enabled`), and `goals`.
3. `PATCH /users/me/settings/notifications` with `{ "enabled": false }` → 200, response shows `settings.notifications.enabled: false`. Repeat with `true`.
4. `PATCH` with `{}` (no `enabled`) → 400 validation error.
5. In the app Settings screen: GENERAL shows only Notification (no Dark Mode / Data sync). Toggle off, reopen the screen → it stays off (loaded from server). Toggle with backend down → it reverts and shows an alert.
