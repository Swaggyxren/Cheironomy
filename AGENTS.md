# AGENTS.md — Cheironomy (Hand Gesture Phone Controller)

This document is the persistent technical contract for any AI agent (Claude Code, Copilot, etc.) working on this repository. Read this in full before writing code. If a request conflicts with this file, this file wins unless the repo owner (Ren) says otherwise in the issue/PR itself.

---

## 1. Project Summary

An Android app that uses the front camera + on-device hand landmark detection to let the user control their phone **without touching the screen** — primarily for use in the shower/bath, where hands are wet and touchscreens don't work reliably.

Core interactions:
- **User-recorded static pose held** (e.g. peace sign, thumb up) → dispatches assigned action (e.g. media play/pause)
- **User-recorded motion gesture** (e.g. swipe, flick, wave, circle) → dispatches assigned action (e.g. simulated swipe/scroll via AccessibilityService)

All recognition relies exclusively on **user-recorded templates** matched via **Nearest-Neighbor ("Best Match Wins")** classification with reject ceilings and runner-up margin checks. There are zero hardcoded or built-in gesture classifiers.

---

## 2. Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose, Material Design 3 Expressive
- **Camera**: CameraX
- **Hand detection**: MediaPipe Tasks Vision — Hand Landmarker (`com.google.mediapipe:tasks-vision`)
- **Signal Filtering**: One Euro Filter (adaptive speed-based smoothing on landmarks and centroid)
- **Matching Algorithm**: Nearest-Neighbor Euclidean (static poses) + DTW with $O(1)$ geometric prefiltering (motion)
- **Min SDK**: 24 (required for `AccessibilityService.dispatchGesture()`)
- **Target SDK**: latest stable at time of build
- **Build**: Gradle (Kotlin DSL preferred, `build.gradle.kts`)
- **CI**: GitHub Actions → persistent keystore signing → build debug APK → send to Telegram via bot API

---

## 3. Module Contracts

| Module | Responsibility | Must NOT do |
|---|---|---|
| `camera/` | CameraX setup, frame analyzer, feeds frames to MediaPipe | No UI, no gesture logic |
| `gesture/` | MediaPipe wrapper, 1€ filter, landmark math, Nearest-Neighbor template matching (`StaticTemplateMatcher`, `MotionTemplateMatcher`, `TrajectoryNormalizer`, `PalmCentroidHelper`) | No direct system calls (no dispatchGesture, no AudioManager) — emits abstract `GestureEvent` only; no hardcoded gesture rules |
| `accessibility/` | `AccessibilityService` impl, builds `GestureDescription`/`StrokeDescription` for swipe/scroll | No camera or MediaPipe code — consumes `GestureEvent` only |
| `media/` | `AudioManager.dispatchMediaKeyEvent()` wrapper for play/pause/next/prev | No camera or MediaPipe code |
| `overlay/` | Floating window: status indicator, on/off toggle, calibration UI | No gesture classification logic |
| `service/` | Foreground service that wires camera → gesture → dispatch together; owns lifecycle | Business logic belongs in the modules above, not here |
| `ui/` | Settings screen: recording flow, custom gestures management, nearest-neighbor threshold tuning | — |

**Data flow direction is one-way**: `camera` → `gesture` → (`accessibility` | `media`). Never let `accessibility` or `media` reach back into `camera` or `gesture` directly — always through the `GestureEvent` interface so modules stay swappable/testable.

---

## 4. Hard Constraints

- **All inference on-device.** No cloud calls, no sending camera frames anywhere. Privacy is non-negotiable.
- **No root, no shell hacks, no Shizuku.** Gesture dispatch only via public Android APIs (`AccessibilityService.dispatchGesture`, `AudioManager.dispatchMediaKeyEvent`). This must work on a stock, unrooted phone.
- **Foreground service required** for the camera+detection loop to survive backgrounding — must declare the correct `foregroundServiceType` (`camera`) for API 34+ manifest requirements.
- **AccessibilityService must degrade gracefully** if the user hasn't granted the permission — app should prompt, not crash.
- **No raw frame storage.** Camera frames are processed in-memory per-frame and discarded. Never write frames to disk.
- **Pure custom templates.** No hardcoded built-in gestures exist in the app; all recognition is driven by user-recorded templates.
- **Nearest-Neighbor matching with margin check.** Live sample must match the closest template below the reject ceiling, and must beat the runner-up template by at least the configurable margin threshold to prevent ambiguous triggers.
- **Debounce everything.** Dispatched actions pass through an explicit gesture state machine with instant reset on pose break / motion decay and a 350ms safety backstop.

---

## 5. Known Gotchas

- `dispatchGesture()` requires API 24+; also requires the service to declare `android:canPerformGestures="true"` in its accessibility-service XML config.
- MediaPipe Hand Landmarker landmark coordinates are normalized `[0,1]` relative to the input image, NOT screen pixels.
- Ephemeral CI runners create unique debug keystores on every run, causing `INSTALL_FAILED_UPDATE_INCOMPATIBLE` during app updates. This is resolved by encoding a persistent project keystore into `SIGNING_KEYSTORE_BASE64` and signing all CI builds with it.
- Foreground service camera type on Android 14+ (API 34) requires explicit `<service android:foregroundServiceType="camera">` in the manifest or the service will crash at runtime.
- `AudioManager.dispatchMediaKeyEvent()` only works if there's an active `MediaSession` in the foreground app — it silently no-ops otherwise. Don't treat lack of response as a bug if no media app is active.

---

## 6. Build & Test Commands

```bash
# Debug build
./gradlew assembleDebug

# Install to connected device/emulator
./gradlew installDebug

# Run lint
./gradlew lint

# Unit tests (gesture math & template matching, no device needed)
./gradlew test
```

---

## 7. CI / Secrets & Keystore Management

GitHub Actions builds and delivers a consistently signed debug APK on every push to `main` (or via manual `workflow_dispatch`).

### Required Secrets (set in `Settings → Secrets and variables → Actions`):
- `TELEGRAM_BOT_TOKEN`: Telegram Bot API token obtained from `@BotFather`.
- `TELEGRAM_CHAT_ID`: Target Telegram chat/group/channel ID to receive the APK.
- `SIGNING_KEYSTORE_BASE64`: Base64-encoded persistent keystore (`cheironomy-signing.jks`).
- `SIGNING_KEYSTORE_PASSWORD`: Keystore password.
- `SIGNING_KEY_ALIAS`: Key alias name (e.g. `cheironomy`).
- `SIGNING_KEY_PASSWORD`: Key alias password.

> [!IMPORTANT]
> **Keystore Backup Policy**: The signing keystore ensures every CI build can update seamlessly over existing installations without requiring an uninstall. If the keystore secret is ever lost or changed, all existing installs will fail to update over old versions and will require an initial manual uninstall to recover. Keep an encrypted backup of `cheironomy-signing.jks` safe outside the git repository (never commit `.jks` files to git).

### APK Naming Convention:
- `cheironomy-<version>-<short-commit-hash>.apk` (e.g., `cheironomy-0.1.0-a1b2c3d.apk`).
