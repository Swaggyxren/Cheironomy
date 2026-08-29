# AGENTS.md — Cheironomy (Hand Gesture Phone Controller)

This document is the persistent technical contract for any AI agent (Claude Code, Copilot, etc.) working on this repository. Read this in full before writing code. If a request conflicts with this file, this file wins unless the repo owner (Ren) says otherwise in the issue/PR itself.

---

## 1. Project Summary

An Android app that uses the front camera + on-device hand landmark detection to let the user control their phone **without touching the screen** — primarily for use in the shower/bath, where hands are wet and touchscreens don't work reliably.

Core interactions:
- **Static pose held** (e.g. open palm) → dispatches a media key event (play/pause)
- **Motion delta** (palm moving across frames) → dispatches a simulated swipe/scroll via AccessibilityService, working system-wide like a real touch gesture

This is NOT a general-purpose "gesture mouse" with cursor tracking — no cursor rendering, no click-and-drag pointer. It's discrete gesture → discrete action, tuned for reliability in challenging conditions (harsh/variable outdoor lighting, backlight, low light, distance from phone).

---

## 2. Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose, Material Design 3 Expressive
- **Camera**: CameraX
- **Hand detection**: MediaPipe Tasks Vision — Hand Landmarker (`com.google.mediapipe:tasks-vision`)
- **Min SDK**: 24 (required for `AccessibilityService.dispatchGesture()`)
- **Target SDK**: latest stable at time of build
- **Build**: Gradle (Kotlin DSL preferred, `build.gradle.kts`)
- **CI**: GitHub Actions → build debug APK → send to Telegram via bot API using repo secrets

---

## 3. Module Contracts

| Module | Responsibility | Must NOT do |
|---|---|---|
| `camera/` | CameraX setup, frame analyzer, feeds frames to MediaPipe | No UI, no gesture logic |
| `gesture/` | MediaPipe wrapper, landmark math, pose classification, motion-delta tracking | No direct system calls (no dispatchGesture, no AudioManager) — emits abstract `GestureEvent` only |
| `accessibility/` | `AccessibilityService` impl, builds `GestureDescription`/`StrokeDescription` for swipe/scroll | No camera or MediaPipe code — consumes `GestureEvent` only |
| `media/` | `AudioManager.dispatchMediaKeyEvent()` wrapper for play/pause/next/prev | No camera or MediaPipe code |
| `overlay/` | Floating window: status indicator, on/off toggle, calibration UI | No gesture classification logic |
| `service/` | Foreground service that wires camera → gesture → dispatch together; owns lifecycle | Business logic belongs in the modules above, not here |
| `ui/` | Settings screen: sensitivity, gesture-to-action mapping, cooldown timing | — |

**Data flow direction is one-way**: `camera` → `gesture` → (`accessibility` | `media`). Never let `accessibility` or `media` reach back into `camera` or `gesture` directly — always through the `GestureEvent` interface so modules stay swappable/testable.

---

## 4. Hard Constraints

- **All inference on-device.** No cloud calls, no sending camera frames anywhere. This app watches someone in the shower — privacy is non-negotiable, not just a preference.
- **No root, no shell hacks, no Shizuku.** Gesture dispatch only via public Android APIs (`AccessibilityService.dispatchGesture`, `AudioManager.dispatchMediaKeyEvent`). This must work on a stock, unrooted phone.
- **Foreground service required** for the camera+detection loop to survive backgrounding — must declare the correct `foregroundServiceType` (`camera`) for API 34+ manifest requirements.
- **AccessibilityService must degrade gracefully** if the user hasn't granted the permission — app should prompt, not crash.
- **No raw frame storage.** Camera frames are processed in-memory per-frame and discarded. Never write frames to disk, even for debugging (use landmark overlay in debug builds instead).
- **Debounce everything.** Every dispatched action (media key or gesture) must pass through a cooldown (default 1.2–1.5s, configurable) — no action may fire more than once per cooldown window regardless of how many frames trigger it.
- **Camera preview is optional for the user, not for the app.** The detection pipeline must work with the preview UI hidden/minimized (e.g. floating overlay only) — don't assume `PreviewView` is always visible.

---

## 5. Known Gotchas

- `dispatchGesture()` requires API 24+; also requires the service to declare `android:canPerformGestures="true"` in its accessibility-service XML config.
- MediaPipe Hand Landmarker landmark coordinates are normalized `[0,1]` relative to the input image, NOT screen pixels — remember to account for camera-to-screen coordinate mapping if ever needed (currently we only use deltas, not absolute mapping, which sidesteps this).
- Front camera + harsh or variable outdoor lighting / strong backlight degrades detection confidence — do not hardcode a high confidence threshold; make it configurable and default conservatively.
- Foreground service camera type on Android 14+ (API 34) requires explicit `<service android:foregroundServiceType="camera">` in the manifest or the service will crash at runtime, not just warn.
- `AudioManager.dispatchMediaKeyEvent()` only works if there's an active `MediaSession` in the foreground app — it silently no-ops otherwise. Don't treat lack of response as a bug if no media app is active.
- Gradle + MediaPipe tasks-vision occasionally has ABI-specific native lib issues — if the build fails on native libs, check `.so` inclusion for the target ABI before assuming it's a Kotlin-side bug.

---

## 6. Build & Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Install to connected device/emulator
./gradlew installDebug

# Run lint
./gradlew lint

# Unit tests (gesture math, no device needed)
./gradlew test
```

Manual test checklist per phase (no automated UI test harness yet — camera/gesture behavior needs physical device testing):
- [ ] Camera permission flow works from cold start
- [ ] AccessibilityService permission prompt appears and re-prompts if revoked
- [ ] Foreground service notification appears and is dismissible correctly
- [ ] App survives screen lock/unlock without crashing the service

---

## 7. Phase Plan (Definition of Done per Phase)

Each phase = one GitHub Issue = one branch = one PR into `main`. Do not combine phases in a single PR unless explicitly instructed.

### Phase 0 — Skeleton
- Empty Compose app, permissions requested (camera, accessibility settings deep-link)
- Foreground service exists and starts/stops, shows notification
- Empty `AccessibilityService` registered (does nothing yet)
- Camera preview visible in a basic screen
- **Done when**: app installs, requests all needed permissions, camera preview renders, service starts without crash

### Phase 1 — MediaPipe Integration
- MediaPipe Hand Landmarker wired into camera frame analyzer
- Landmarks drawn as debug overlay on camera preview (visual confirmation only, no actions)
- **Done when**: hand landmarks visibly track a real hand in the debug overlay in real time

### Phase 2 — Static Pose → Play/Pause
- Open-palm pose classifier (geometric rule on landmarks, no dataset/model training)
- On sustained open-palm hold (~0.5s) → `media` module dispatches `KEYCODE_MEDIA_PLAY_PAUSE`
- Cooldown applied
- **Done when**: holding an open palm toggles play/pause in a real media app (e.g. YouTube) reliably across 10 consecutive tries

### Phase 3 — Motion Delta → Swipe/Scroll
- Track palm centroid position across frames; classify horizontal/vertical motion past a threshold as swipe-left/right or scroll-up/down
- `accessibility` module builds and dispatches the corresponding `GestureDescription`
- **Done when**: swiping a hand left/right or up/down produces the equivalent touch gesture in a real app (e.g. a feed scrolls, a page swipes)

### Phase 4 — Reliability Tuning
- Configurable confidence threshold, configurable cooldown
- Handle low-confidence/no-hand-detected states gracefully (no false triggers)
- Test specifically under variable outdoor lighting conditions (direct sunlight, strong backlight, dusk/low light)
- **Done when**: false-positive rate is acceptably low in outdoor bath/shower lighting conditions, not just clean-room testing

### Phase 5 — Settings & Polish
- Settings screen: sensitivity slider, gesture-to-action remapping, cooldown timing
- Floating overlay: on/off toggle, live status indicator (detecting / no hand / cooldown)
- **Done when**: user can fully configure and toggle the feature without editing code

### Phase 6 — Custom Gesture Recording (Static + Motion)
- User-recorded custom hand motions (swipes, waves, shapes, flicks) with origin translation, scale normalization, arc-length resampling, O(1) geometric prefiltering, and speed-invariant Dynamic Time Warping (DTW) matching.
- User-recorded custom held hand poses with wrist-relative translation and palm scale invariance.
- In-app interactive recording flow with real-time camera feedback, countdown, path preview canvas, action assignment, and custom gestures management in Settings.
- **Done when**: user can record any custom motion or static pose, assign it to an action, and perform it reliably without false triggers or ML training.

---

## 8. GitHub Workflow Rules (for agents)

- Work off the phase branch named in the issue (`phase-N-<name>`), never commit directly to `main`
- One logical change per commit; commit messages describe *what* and *why*, not just *what*
- Open a PR into `main` when a phase's "Done when" criteria are met — do not mark a phase done without meeting all listed criteria
- CI (GitHub Actions) builds a debug APK on every push and delivers it to Telegram with filename `cheironomy-<version>-<short-commit-hash>.apk` — do not change this delivery pattern without discussion
- If a gotcha in Section 5 is hit and worked around, document the fix in this file's Section 5, not just in a commit message — this file is the persistent memory for future agent sessions

---

## 9. CI / Secrets

GitHub Actions builds and delivers a debug APK on every push to `main` (or via manual `workflow_dispatch`).

- **Required Secrets** (set in `Settings → Secrets and variables → Actions`):
  - `TELEGRAM_BOT_TOKEN`: Telegram Bot API token obtained from `@BotFather`.
  - `TELEGRAM_CHAT_ID`: Target Telegram chat/group/channel ID to receive the APK.
- **APK Naming Convention**:
  - `cheironomy-<version>-<short-commit-hash>.apk` (e.g., `cheironomy-0.1.0-a1b2c3d.apk`).

