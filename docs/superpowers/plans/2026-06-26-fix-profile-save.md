# Fix Edit Profile Save Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `PUT /users/me/profile` reliably persist every Edit Profile field (including bloodType & conditions), stop the gender-500 and birthDate-400 errors, and round-trip the data back through `/auth/me`.

**Architecture:** Add `bloodType`/`conditions` to the existing `UserProfile` model + request/response DTOs (no new `healthMetrics` structure). `UserService.updateProfile` parses gender safely (invalid → skip, never throw). The shared `UserResponseMapper` emits `birthDate` (Date→`yyyy-MM-dd`), `bloodType`, `conditions`. The React Native Edit Profile form uses a 3-choice gender picker, sends `birthDate` as a plain `yyyy-MM-dd` string, and reads all fields from `profile.*` (the phantom `healthMetrics` is dropped).

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data MongoDB, JUnit 5 + Mockito (`spring-boot-starter-test`). Frontend: React Native 0.82 / TypeScript, Axios.

## Global Constraints

- Route is `PUT /users/me/profile` (no `/api` prefix); `/users/**` is JWT-gated — do NOT touch SecurityConfig.
- `Gender` enum values are exactly `MALE`, `FEMALE`, `OTHER`. Gender input that is null, blank, or not one of these → the profile's gender is left unchanged (NEVER throw).
- `birthDate` wire format is `yyyy-MM-dd` (BE `UpdateProfileRequest.birthDate` is `Date` with `@JsonFormat(pattern="yyyy-MM-dd")` + `@Past`). FE sends the raw `yyyy-MM-dd` string or `null` — never an ISO datetime.
- `bloodType` (String) and `conditions` (`List<String>`) live on `UserProfile`, NOT a separate healthMetrics object.
- `UserProfileDto` exposes `birthDate` as a `yyyy-MM-dd` String (replacing the old unused `Integer birthday` field).
- All `User→UserResponse` mapping goes through `UserResponseMapper` (single source); `updateProfile` already returns via it.
- Tests must NOT use `@SpringBootTest` (no live MongoDB/Redis). Pure unit test for the mapper; Mockito for the service.
- Running backend tests on this machine requires the Avast-truststore + cached Maven 3.9.11 workaround (below).

### Backend test command (this machine only)

```
cd /d/DATN/DACN2_BEserver
export JAVA_HOME="/c/Program Files/Microsoft/jdk-17.0.17.10-hotspot"
export MAVEN_OPTS="-Djavax.net.ssl.trustStore=C:\\Users\\ASUS\\AppData\\Local\\Temp\\cacerts-avast -Djavax.net.ssl.trustStorePassword=changeit"
MVN="/c/Users/ASUS/.m2/wrapper/dists/apache-maven-3.9.11/03d7e36a140982eea48e22c1dcac01d8862b2550b2939e09a0809bbc5182a5bc/bin/mvn"
"$MVN" test -Dtest=SomeTestClass
```

Use only focused `-Dtest=...`; never the full suite.

---

## File Structure

**Backend (`DACN2_BEserver`):**
- Modify `src/main/java/com/example/dacn2_beserver/model/user/UserProfile.java` — add `bloodType`, `conditions`.
- Modify `src/main/java/com/example/dacn2_beserver/dto/user/UpdateProfileRequest.java` — add `bloodType`, `conditions`.
- Modify `src/main/java/com/example/dacn2_beserver/dto/user/UserProfileDto.java` — replace `birthday:Integer` with `birthDate:String`; add `bloodType`, `conditions`.
- Modify `src/main/java/com/example/dacn2_beserver/dto/user/UserResponseMapper.java` — map `birthDate`/`bloodType`/`conditions`.
- Modify `src/main/java/com/example/dacn2_beserver/service/user/UserService.java` — safe gender parse + set new fields.
- Modify `src/test/java/com/example/dacn2_beserver/dto/user/UserResponseMapperTest.java` — cover new fields.
- Modify `src/test/java/com/example/dacn2_beserver/service/user/UserServiceTest.java` — cover updateProfile.

**Frontend (`DACN2_FEserver`):**
- Modify `src/components/Home/HeaderSection/types.ts` — add `bloodType`/`conditions` to `profile`.
- Modify `src/screens/AppScreen/Setting/SettingScreen.tsx` — gender picker, payload, read from `profile.*`.

---

## Task 1: Backend model + DTO fields (data shape)

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/model/user/UserProfile.java`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/user/UpdateProfileRequest.java`
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/user/UserProfileDto.java`

**Interfaces:**
- Produces: `UserProfile.getBloodType()/setBloodType(String)`, `getConditions()/setConditions(List<String>)`; `UpdateProfileRequest.getBloodType()/getConditions()`; `UserProfileDto` builder fields `birthDate(String)`, `bloodType(String)`, `conditions(List<String>)` (and removal of `birthday(Integer)`). Consumed by Tasks 2 & 3.

This task is a pure data-shape change with no behavior, so it has no standalone test; its correctness is verified by compilation here and exercised by Tasks 2/3's tests.

- [ ] **Step 1: Add fields to UserProfile model**

In `UserProfile.java`, add two fields after `weightKg` (the class already has Lombok `@Getter/@Setter/@Builder`):

```java
    private String bloodType;          // optional
    private java.util.List<String> conditions;  // optional
```

- [ ] **Step 2: Add fields to UpdateProfileRequest**

In `UpdateProfileRequest.java`, add after `weightKg`:

```java
    private String bloodType;
    private java.util.List<String> conditions;
```

- [ ] **Step 3: Update UserProfileDto**

In `UserProfileDto.java`, remove the line `private Integer birthday;` and add:

```java
    private String birthDate;          // yyyy-MM-dd
    private String bloodType;
    private java.util.List<String> conditions;
```

Keep existing `fullName`, `avatarUrl`, `gender`, `heightCm`, `weightKg`.

- [ ] **Step 4: Compile to verify the shape**

Run: `"$MVN" -o test-compile 2>&1 | tail -15`
Expected: BUILD SUCCESS. (If `-o` fails on missing artifacts, drop `-o`.) Note: this may surface a compile error in `UserResponseMapper` ONLY if something already referenced `birthday(...)` — it does not (verified), so compilation should pass.

- [ ] **Step 5: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/model/user/UserProfile.java \
        src/main/java/com/example/dacn2_beserver/dto/user/UpdateProfileRequest.java \
        src/main/java/com/example/dacn2_beserver/dto/user/UserProfileDto.java
git commit -m "feat(user): add bloodType/conditions and birthDate to profile DTOs"
```

---

## Task 2: Mapper emits birthDate/bloodType/conditions

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/dto/user/UserResponseMapper.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/dto/user/UserResponseMapperTest.java`

**Interfaces:**
- Consumes: `UserProfile.getBloodType()/getConditions()/getBirthday()` (Task 1 + existing `getBirthday():Date`); `UserProfileDto` builder fields from Task 1.
- Produces: mapped `UserResponse.profile` now carries `birthDate` (yyyy-MM-dd), `bloodType`, `conditions`.

- [ ] **Step 1: Write the failing test additions**

In `UserResponseMapperTest.java`, add a new test (and the import `java.util.List`, `java.util.Calendar` or use `java.text.SimpleDateFormat` to build a Date — use the approach shown):

```java
    @Test
    void mapsBirthDateBloodTypeAndConditions() throws Exception {
        java.util.Date dob = new java.text.SimpleDateFormat("yyyy-MM-dd").parse("2000-01-15");
        User u = User.builder().id("u5").username("dob")
                .profile(UserProfile.builder()
                        .fullName("Has DOB")
                        .birthday(dob)
                        .bloodType("O+")
                        .conditions(java.util.List.of("asthma", "penicillin allergy"))
                        .build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getProfile().getBirthDate()).isEqualTo("2000-01-15");
        assertThat(r.getProfile().getBloodType()).isEqualTo("O+");
        assertThat(r.getProfile().getConditions()).containsExactly("asthma", "penicillin allergy");
    }

    @Test
    void nullBirthdayMapsToNullBirthDate() {
        User u = User.builder().id("u6").username("nodob")
                .profile(UserProfile.builder().fullName("No DOB").build())
                .build();

        UserResponse r = UserResponseMapper.toResponse(u);

        assertThat(r.getProfile().getBirthDate()).isNull();
        assertThat(r.getProfile().getBloodType()).isNull();
        assertThat(r.getProfile().getConditions()).isNull();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$MVN" test -Dtest=UserResponseMapperTest`
Expected: FAIL — `UserProfileDto` has no `getBirthDate()`/`getBloodType()`/`getConditions()` yet wired in the mapper (compile error, or assertion failure because mapper doesn't set them).

- [ ] **Step 3: Update mapProfile**

In `UserResponseMapper.java`, the `mapProfile` builder currently ends:

```java
                .fullName(p.getFullName())
                .avatarUrl(p.getAvatarUrl())
                .gender(p.getGender() == null ? null : p.getGender().name())
                .heightCm(p.getHeightCm())
                .weightKg(p.getWeightKg())
                .build();
```

Replace that builder chain with:

```java
                .fullName(p.getFullName())
                .avatarUrl(p.getAvatarUrl())
                .gender(p.getGender() == null ? null : p.getGender().name())
                .birthDate(formatBirthDate(p.getBirthday()))
                .heightCm(p.getHeightCm())
                .weightKg(p.getWeightKg())
                .bloodType(p.getBloodType())
                .conditions(p.getConditions())
                .build();
```

Add a private helper to the class (and import `java.text.SimpleDateFormat`, `java.util.Date`):

```java
    private static String formatBirthDate(Date birthday) {
        if (birthday == null) {
            return null;
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(birthday);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `"$MVN" test -Dtest=UserResponseMapperTest`
Expected: PASS — all tests green (the prior tests plus the 2 new ones).

- [ ] **Step 5: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/dto/user/UserResponseMapper.java \
        src/test/java/com/example/dacn2_beserver/dto/user/UserResponseMapperTest.java
git commit -m "feat(user): map birthDate/bloodType/conditions in UserResponseMapper"
```

---

## Task 3: Safe gender parse + persist new fields

**Files:**
- Modify: `DACN2_BEserver/src/main/java/com/example/dacn2_beserver/service/user/UserService.java`
- Test: `DACN2_BEserver/src/test/java/com/example/dacn2_beserver/service/user/UserServiceTest.java`

**Interfaces:**
- Consumes: `UpdateProfileRequest.getBloodType()/getConditions()` (Task 1); `UserProfile.setBloodType/setConditions` (Task 1); `UserResponseMapper.toResponse` (existing).
- Produces: `updateProfile` persists gender only when valid, plus bloodType/conditions.

- [ ] **Step 1: Write the failing test additions**

In `UserServiceTest.java`, add tests for `updateProfile` (the test class already has `@ExtendWith(MockitoExtension.class)`, `@Mock UserRepository userRepository`, `@InjectMocks UserService userService`; reuse them). Add imports as needed (`UpdateProfileRequest`, `UserProfile`, `Gender`):

```java
    @Test
    void updateProfileSetsValidGender() {
        User u = User.builder().id("u1").username("dat")
                .profile(UserProfile.builder().build()).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = UpdateProfileRequest.builder().gender("male").build();
        UserResponse res = userService.updateProfile("u1", req);

        assertThat(res.getProfile().getGender()).isEqualTo("MALE");
    }

    @Test
    void updateProfileIgnoresBlankGender() {
        User u = User.builder().id("u1").username("dat")
                .profile(UserProfile.builder().gender(com.example.dacn2_beserver.model.enums.Gender.FEMALE).build())
                .build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = UpdateProfileRequest.builder().gender("").build();
        UserResponse res = userService.updateProfile("u1", req);

        // blank gender ignored -> previous FEMALE retained, no exception
        assertThat(res.getProfile().getGender()).isEqualTo("FEMALE");
    }

    @Test
    void updateProfileIgnoresInvalidGender() {
        User u = User.builder().id("u1").username("dat")
                .profile(UserProfile.builder().build()).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = UpdateProfileRequest.builder().gender("xyz").build();
        UserResponse res = userService.updateProfile("u1", req); // must NOT throw

        assertThat(res.getProfile().getGender()).isNull();
    }

    @Test
    void updateProfileSavesBloodTypeAndConditions() {
        User u = User.builder().id("u1").username("dat")
                .profile(UserProfile.builder().build()).build();
        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .bloodType("O+")
                .conditions(java.util.List.of("asthma"))
                .build();
        UserResponse res = userService.updateProfile("u1", req);

        assertThat(res.getProfile().getBloodType()).isEqualTo("O+");
        assertThat(res.getProfile().getConditions()).containsExactly("asthma");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$MVN" test -Dtest=UserServiceTest`
Expected: FAIL — `updateProfileIgnoresInvalidGender` throws `IllegalArgumentException` (current `Gender.valueOf` behavior), and bloodType/conditions assertions fail (not yet persisted).

- [ ] **Step 3: Update updateProfile**

In `UserService.java`, replace the gender block:

```java
        if (req.getGender() != null) {
            p.setGender(Gender.valueOf(req.getGender().toUpperCase()));
        }
```

with a safe parse:

```java
        Gender parsedGender = parseGender(req.getGender());
        if (parsedGender != null) {
            p.setGender(parsedGender);
        }
```

Add the new field sets after the `weightKg` set (before the `fullName`/`avatarUrl` block is fine too):

```java
        if (req.getBloodType() != null) p.setBloodType(req.getBloodType());
        if (req.getConditions() != null) p.setConditions(req.getConditions());
```

Add a private helper method to the class:

```java
    private Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
```

(`Gender` is already imported in `UserService`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `"$MVN" test -Dtest=UserServiceTest`
Expected: PASS — all tests green (prior notification tests + the 4 new updateProfile tests).

- [ ] **Step 5: Commit**

```bash
cd /d/DATN/DACN2_BEserver
git add src/main/java/com/example/dacn2_beserver/service/user/UserService.java \
        src/test/java/com/example/dacn2_beserver/service/user/UserServiceTest.java
git commit -m "fix(user): parse gender safely and persist bloodType/conditions"
```

---

## Task 4: Frontend — gender picker, payload, read from profile

**Files:**
- Modify: `DACN2_FEserver/src/components/Home/HeaderSection/types.ts`
- Modify: `DACN2_FEserver/src/screens/AppScreen/Setting/SettingScreen.tsx`

**Interfaces:**
- Consumes: backend `PUT /users/me/profile` now accepts bloodType/conditions and tolerates blank/invalid gender (Tasks 1-3); `/auth/me` now returns `profile.birthDate`/`bloodType`/`conditions` (Task 2).
- Produces: none (leaf UI change).

- [ ] **Step 1: Add bloodType/conditions to the profile type**

In `src/components/Home/HeaderSection/types.ts`, inside the `profile?: { ... }` object of `UserProfile`, add (alongside the existing fields like `weightKg`):

```ts
    bloodType?: string;
    conditions?: string[];
```

(`birthDate?: string` already exists in this type.)

- [ ] **Step 2: Replace the gender TextInput with a 3-choice picker**

In `SettingScreen.tsx`, the Edit Profile form currently has:

```tsx
            <Text style={styles.formLabel}>Giới tính</Text>
            <TextInput
              value={form.gender}
              onChangeText={gender => setForm(prev => ({ ...prev, gender }))}
              style={styles.formInput}
              placeholder="male/female/other"
            />
```

Replace it with a row of three selectable buttons (uses `TouchableOpacity`, already imported; reuses existing `styles.formInput` container width via inline fl-direction):

```tsx
            <Text style={styles.formLabel}>Giới tính</Text>
            <View style={{ flexDirection: 'row', gap: 8, marginBottom: 12 }}>
              {(['MALE', 'FEMALE', 'OTHER'] as const).map(option => {
                const selected = form.gender === option;
                return (
                  <TouchableOpacity
                    key={option}
                    onPress={() => setForm(prev => ({ ...prev, gender: option }))}
                    style={[
                      styles.formInput,
                      {
                        flex: 1,
                        marginBottom: 0,
                        alignItems: 'center',
                        backgroundColor: selected ? '#2D8C83' : '#F3F4F6',
                      },
                    ]}
                  >
                    <Text style={{ color: selected ? '#FFFFFF' : '#374151' }}>
                      {option}
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
```

- [ ] **Step 3: Fix the save payload (birthDate + gender)**

In `handleSaveProfile`, the payload currently builds:

```tsx
        gender: form.gender.trim(),
        birthDate: form.birthDate
          ? new Date(form.birthDate).toISOString()
          : null,
```

Replace those two lines with:

```tsx
        gender: form.gender || null,
        birthDate: form.birthDate ? form.birthDate.slice(0, 10) : null,
```

Leave the other payload fields (`fullName`, `avatarUrl`, `heightCm`, `weightKg`, `bloodType`, `conditions`) as they are.

- [ ] **Step 4: Read fields from profile.* instead of healthMetrics.***

In `syncUser`, replace the `setForm({...})` block's data sources so it reads from `profile` (remove reliance on `metrics`/`healthMetrics`). The block currently reads from both `profile` and `metrics`; change it to:

```tsx
      setForm({
        fullName: profile.fullName || nextUser.username || '',
        avatarUrl: profile.avatarUrl || '',
        gender: profile.gender || '',
        birthDate: (profile.birthDate || '').slice(0, 10),
        heightCm: String(profile.heightCm || ''),
        weightKg: String(profile.weightKg || ''),
        bloodType: profile.bloodType || '',
        conditions: (profile.conditions || []).join(', '),
      });
```

Also remove the now-unused `const metrics = nextUser.healthMetrics || {};` line just above `setForm`.

- [ ] **Step 5: Fix the healthRows display to read from profile**

The `healthRows` useMemo currently reads `metrics?.heightCm`, `metrics?.weightKg`, `metrics?.bloodType` and `const metrics = user?.healthMetrics || null;`. Replace the `metrics` const and `healthRows` so they read from `profile`:

Replace:
```tsx
  const metrics = user?.healthMetrics || null;
```
with nothing (delete the line), and update `healthRows`:

```tsx
  const healthRows = useMemo(
    () => [
      { label: 'Chiều cao', value: `${profile?.heightCm || 0} cm` },
      { label: 'Cân nặng', value: `${profile?.weightKg || 0} kg` },
      { label: 'Giới tính', value: profile?.gender || 'Chưa cập nhật' },
      { label: 'Nhóm máu', value: profile?.bloodType || 'Chưa cập nhật' },
    ],
    [profile],
  );
```

Also update the "Health notes" SettingRow value (currently `(metrics?.conditions || []).join(', ')`) to `(profile?.conditions || []).join(', ') || 'No notes'`.

- [ ] **Step 6: Lint and type-check**

Run:
```
cd /d/DATN/DACN2_FEserver
npm run lint 2>&1 | tail -25
npx --no-install tsc --noEmit 2>&1 | grep -E "SettingScreen|HeaderSection/types" || echo "no type errors in changed files"
```
Expected: no new ESLint errors in the two changed files; no tsc errors referencing them. (Pre-existing warnings / unrelated test-file tsc errors are fine.)

- [ ] **Step 7: Commit**

```bash
cd /d/DATN/DACN2_FEserver
git add src/components/Home/HeaderSection/types.ts \
        src/screens/AppScreen/Setting/SettingScreen.tsx
git commit -m "fix(settings): gender picker, yyyy-MM-dd birthDate, read profile fields"
```

---

## Manual verification (after all tasks)

1. Start backend on a non-intercepted network.
2. In the app: Settings → Edit personal profile. Pick a gender, set birthday `2000-01-01`, height/weight, blood type `O+`, conditions `asthma, dust allergy`. Tap "Lưu hồ sơ".
3. Expect the success alert ("Đã lưu"), no error. Reopen Edit Profile → all fields, including blood type and conditions, are populated from the server.
4. `GET /auth/me` → `profile` includes `birthDate` (`2000-01-01`), `bloodType`, `conditions`.
5. Sanity: leaving gender unselected then saving must NOT error (gender simply unset server-side).
