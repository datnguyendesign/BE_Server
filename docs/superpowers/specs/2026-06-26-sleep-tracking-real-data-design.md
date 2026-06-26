# Sleep Tracking — Real Data + Logging — Design Spec

**Date:** 2026-06-26
**Status:** Approved (design)
**Scope:** Spring Boot backend (`DACN2_BEserver`) + React Native app (`DACN2_FEserver`)

## Problem

The backend can already store rich sleep data (`SleepSession` with stage breakdown, daily
aggregation, readiness integration), but the mobile **Sleep screen runs entirely on mock data**
(`MOCK_SLEEP_DATA`, `MOCK_WEEKLY`) and there is **no UI for a user to log their sleep**. The
sleep goal is also inconsistent across the stack (backend highlights use 7h; the FE home card
uses 8h).

## Goal

Replace all mock sleep data in the app with real backend data, and let users log sleep two ways
(a live in-app timer and a manual entry form). Keep all sleep health logic on the backend as the
single source of truth.

## Decisions (locked)

- **Logging UX:** Both a live timer (start/stop) and a manual entry form.
- **Stages:** Backend estimates deep/REM/light/awake from total duration when no real segment
  data is provided; estimates are clearly flagged as estimated, not measured.
- **Sleep score:** Backend computes a real 0–100 score from duration vs goal.
- **Sleep goal:** Fixed, unified **8h (480 min)** everywhere (score, progress, highlights, home
  card, calendar). No user-configurable goal in this iteration.
- **History:** Real 7-day trend from the existing summary range endpoint.
- **Form scope:** Minimal + safety guards — pre-fill last night, prevent future entries, and
  replace (not add) when a day already has a logged session.

## Approach

**Approach A — Backend-owned.** Health logic (score, stage estimation) lives on the backend as
pure, isolated, unit-testable components mirroring the existing `ReadinessScorer` style. All
sleep numbers are served from the existing `DailyAggregate` (extended with one `sleepScore`
field) via the existing summary endpoints. The FE only fetches and renders — no health logic in
the app. This reuses the aggregate path, so Home and Calendar automatically stay consistent with
the Sleep screen.

(Approaches B "frontend-computed" and C "full sleep subsystem with insights/correlations" were
rejected: B duplicates health logic in the app and conflicts with the backend-score decision; C
over-builds beyond the stated goal — insights/coaching/configurable goals are deferred.)

## Data flow

```
User logs sleep (timer stop OR manual form save)
        │  POST /health/sleep  { time:{startAt,endAt}, meta:{source} }
        ▼
SleepService.create()
   ├─ if no real segments → SleepStageEstimator derives deep/REM/light/awake from duration
   ├─ future-entry guard (reject endAt in the future)
   ├─ save SleepSession (source = "manual"/"timer", estimated flag)
   └─ DailyAggregateService.addSleep(... , replace=true for manual/timer)
        ▼
DailyAggregate (sleepMinutes + stage minutes + NEW sleepScore)
        ▼
GET /health/summary/{date}   and   GET /health/summary?from&to  (7-day range)
        ▼
FE SleepTrackingScreen renders real dial/score, schedule, stages, weekly trend
   (Home card + Calendar read the same aggregate → consistent automatically)
```

## Backend components

### New (pure, no I/O, unit-testable)

**`SleepStageEstimator`** (`service/health/`)
- Input: `totalMinutes`. Output: `{deep, rem, light, awake}` minutes.
- Typical adult ratios applied to time asleep: ~13% deep, ~22% REM, ~60% light, ~5% awake.
- Rounds so the four stages sum back to `totalMinutes`.

**`SleepScorer`** (`service/health/`)
- Input: `totalMinutes`, `goalMinutes` (480). Output: `int score` (0–100) + short Vietnamese
  `quality` label.
- Duration-vs-goal curve: full credit in the healthy band (7–9h), tapering below 6h and above
  ~9.5h. Same spirit as `ReadinessScorer.sleepPoints()` but standalone and scaled 0–100.
- v1 scores on duration vs goal only. Stage estimates are derived from duration and therefore
  carry no independent signal yet (scoring on them would be circular); they remain available as
  an input for a future refinement.

### Changes to existing files

1. **`SleepService.create()`**
   - When `req.getSegments()` is empty, call `SleepStageEstimator` to fill stage minutes.
   - Tag the session source (`manual` / `timer`) and an `estimated=true` marker.
   - Add a future-entry guard: reject when `endAt` is in the future (400).

2. **`DailyAggregateService.addSleep(...)`**
   - Add a `replace` mode. For manual/timer logs, replace that day's sleep fields instead of
     cumulative add, so re-logging a night corrects rather than double-counts.
   - Recompute and store `sleepScore` via `SleepScorer` here.

3. **Constants / highlights**
   - Change `DEFAULT_SLEEP_GOAL_MIN` from 420 → **480 (8h)**.
   - Update `applySleepHighlights` thresholds and Vietnamese messages to the 8h goal.

4. **Model + DTOs**
   - Add `Integer sleepScore` to `DailyAggregate`, `DailyAggregateResponse`, and
     `CalendarDaySummaryResponse`. Reuse the existing stage fields. One new field, surfaced
     wherever sleep already appears.

## Frontend changes

### Services (`services/`)
- `createSleepSession({ startAt, endAt, source })` → `POST /health/sleep`.
- Reuse existing `fetchDayAggregate(date)` and the summary range fetch for today + last 7 days.

### `SleepTrackingScreen`
- On focus, fetch today's aggregate + last 7 days. Remove `MOCK_SLEEP_DATA` / `MOCK_WEEKLY`.
- Map real fields → existing components:
  - `SleepDial` ← `sleepScore` + total sleep (from `sleepMinutes`)
  - `ScheduleRow` ← bedtime/wake from the day's latest session time range
  - `SleepStages` ← deep/REM/light minutes → percents; show an "estimated" caption when the
    source is estimated
  - `WeeklyTrend` ← 7-day `sleepMinutes` → hours
- Empty state: when no sleep is logged for the day, show a "No sleep logged — Log your sleep"
  prompt instead of zeros.

### Logging UI
- A "Log sleep" entry point (header button / FAB) opening a modal/bottom-sheet with two modes:
  1. **Timer:** "Start sleep" records `startAt` locally (persisted so it survives app close);
     "I'm awake" sends `startAt → now`; shows elapsed time while active.
  2. **Manual form:** bedtime + wake-time pickers pre-filled to last night (e.g. 23:00 → 07:00),
     Save → `createSleepSession`. Client guard mirrors backend: block future times; warn if it
     will replace an existing entry.
- On save/stop: refetch and update the screen. Home card and Calendar pick up the same data on
  their next load.

### Consistency cleanup
- Align the Home card status thresholds + target and the calendar status logic to the unified 8h
  goal so all three surfaces agree.

### State
- Keep state local to the screen + service calls. The existing mock `SleepContext` is removed or
  repurposed to hold the real fetched day. No new global context.

## Error handling

| Case | Behavior |
|---|---|
| Future `endAt` on log | Backend rejects (400); FE blocks before sending, shows inline message |
| `endAt` not after `startAt` (overnight) | Backend validates; FE normalizes overnight ranges (wake-time next day) before sending |
| Re-logging an already-logged day | Backend replaces the day's sleep; FE warns "This will replace your existing entry" |
| Network failure on save | FE keeps form/timer state, shows retry toast; timer `startAt` persisted so an in-progress session is not lost |
| No sleep data for the day | FE shows empty state with a "Log sleep" prompt, not zeros |
| Aggregate fetch fails | FE shows non-blocking error + retry; screen does not crash |

## Testing

- **Backend unit (pure):** `SleepStageEstimator` (stages sum to total, sensible split, edge
  durations). `SleepScorer` (healthy band = top score, boundary values, zero, very high).
- **Backend service:** `SleepService.create()` fills estimated stages when no segments;
  future-entry rejected; `addSleep` replace-mode overwrites rather than adds.
- **FE:** screen maps a fetched aggregate into components (no mock constants); manual form blocks
  future times. Keep light, matching the project's existing Jest coverage level.

## Out of scope (deferred to future specs)

- Auto / sensor-based sleep detection
- Real stage measurement from device sensors (motion, heart rate)
- User-configurable sleep goal + settings UI
- Insights / correlations / coaching
- Tap-a-day history detail view
