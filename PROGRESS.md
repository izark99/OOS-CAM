# Progress

STATUS: PHASE 2 IN PROGRESS

## Phase 0: Spike + scaffold (gate G0)

- [x] T0.1 Add `LICENSE` (Apache-2.0 full text from https://www.apache.org/licenses/LICENSE-2.0.txt) and `NOTICE`.
- [x] T0.2 Make `scripts/check_env.sh` pass in the cloud VM (after `scripts/setup_cloud.sh`): JDK 21, Android SDK platform and build-tools, `ANDROID_HOME`, `local.properties` written by the script, never committed.
- [x] T0.3 Gradle wrapper, version catalog, `settings.gradle.kts` with `:core` (JVM) and `:app` (Android, Compose). `gradle.properties`: `org.gradle.jvmargs=-Xmx4g`, `org.gradle.parallel=true`, `org.gradle.caching=true`.
- [x] T0.4 `:app` skeleton: package `io.github.ooscam`, minSdk 31, one Compose activity showing "OOS Cam", no INTERNET permission. `./gradlew assembleDebug lint` green.
- [x] T0.5 `:core:runFixtures` JavaExec task that reads `<fixturesDir>` and writes empty `export_actual.json` / `segment_actual.json` arrays (wiring only; Phase 1 fills it).
- [x] T0.6 Spike screen `SpikeActivity` (debug build only): starts a camera|microphone foreground service that records 2 CameraX segments of 20 s back to back, logs the gap in ms, and a button that runs a Media3 Transformer job (2 clips, trim, 2x on a range, remove audio) and logs the time. Unit-test only the parts that run on the JVM.
- [x] T0.7 Write `docs/SPIKE.md`: build times measured in the cloud (clean `assembleDebug`, `:core:test`), plus a checklist the owner runs on the phone with the debug APK (see "Needs owner").

## Phase 1: Core logic (gate G1)

- [x] T1.1 `:core` models: `EditProject`, `ProjectClip`, `SpeedRange`, `ExportPlan`, `PlanPart`, `RecordingInput`, `SegmentPlan` with kotlinx-serialization matching `testcase/` JSON shapes.
- [x] T1.2 `planExport` tier 1: order, trim validation (E6), trimmed sum (E2), clip count (E1).
- [x] T1.3 `planExport` tier 2: range clipping (W1), E3, E4, E7, part splitting, equal-speed merge, round half up with exact integer math.
- [x] T1.4 `planExport` tier 3: E5, audio, export height (W2), error result shape (R11).
- [x] T1.5 `planSegments`: bitrate presets, time and SD size caps, low-storage stop, stop reason tie rule, file names (R12–R14).
- [x] T1.6 `runFixtures` writes real results; `python3 testcase/run_acceptance.py --suite core` passes for base and v2.
- [x] T1.7 Unit tests in `:core` for every rule R1–R14 written from `TEST_RULES.md`, independent of the fixture files.

## Phase 2: Capture (gate G2)

- [x] T2.1 `CaptureService` (LifecycleService, foreground type camera|microphone), started only from a visible activity; notification channel, ongoing notification with elapsed time and Stop action.
- [x] T2.2 Recorder controller driven by `planSegments` rules: new `Recording` at each segment boundary, finalize on stop.
- [x] T2.3 Free-space sampler every 5 s and low-storage stop; thermal status SEVERE stop; phone call stop (TelephonyCallback, no extra permission if not granted: skip).
- [x] T2.4 Wake lock while recording; "Screen off" action dims to black then lets the system turn the screen off.
- [ ] T2.5 Permission flow: camera, mic (optional), notifications; behaviour per `docs/SPEC.md` permissions matrix.
- [ ] T2.6 Storage targets: MediaStore `Movies/OOS Cam`, SD card via SAF tree URI; segment names from `:core`.
- [ ] T2.7 Room: `RecordingSession`, `Segment` tables, schema exported to `app/schemas/`, migration test scaffold.
- [ ] T2.8 Robolectric tests named `*Capture*Test` for the service state machine, notification content, stop reasons and storage target choice (≥ 15 tests).

## Phase 3: Library + editor (gate G3)

- [ ] T3.1 Library data: sessions with grouped segments from Room + MediaStore, damaged detection (unfinalized MP4).
- [ ] T3.2 Library screen: grid, play (system player intent), share, delete with confirm, multi-select, "Edit selected".
- [ ] T3.3 Editor view model holding an `EditProject`; reorder, trim (0.1 s steps), speed ranges, mute; validation messages from `planExport` errors and warnings.
- [ ] T3.4 Import from the system photo picker (READ_MEDIA_VIDEO only if the picker is unavailable).
- [ ] T3.5 `ExportEngine`: build a Media3 `Composition` only from `ExportPlan`; progress, cancel, output name `OOSCam_edit_YYYYMMDD_HHMMSS.mp4`.
- [ ] T3.6 `AcceptanceUseCaseTest`: build the X01 project through the editor view model (fake clips with X01 durations from the fixture file read at test time) and write `app/build/acceptance/X01_plan.json`.
- [ ] T3.7 Room: `EditProject`, `ProjectClip`, `SpeedRange` tables + migration test from version 1.
- [ ] T3.8 Unit tests for view models and export plan-to-composition mapping.

## Phase 4: Glass UI + i18n (gate G4)

- [ ] T4.1 Generate `ui/theme/Tokens.kt` from `design/design-system/oos-cam/tokens.json` with a Gradle task; Inter fonts bundled (OFL, download from github.com/rsms/inter releases).
- [ ] T4.2 Glass modifier: RenderEffect blur over the content behind, opaque `glass-solid` fallback when "Reduce glass effects" is on or power saver is active.
- [ ] T4.3 Components: buttons (44/56 only), icon button, segmented, dropdown, checkbox, switch, search field, sheet, toast, progress, skeleton, badge, list row, timeline, trim handle.
- [ ] T4.4 Apply to all screens to match `design/screens/*.png` (layout numbers in `docs/DESIGN.md`).
- [ ] T4.5 Settings screen and DataStore preferences (segment length, quality, mic default, storage target, reduce glass, language).
- [ ] T4.6 `values-vi/strings.xml` for every string; per-app language (Android 13+ locale config).
- [ ] T4.7 Roborazzi tests: one screenshot per screen named like `design/screens/*.html` (11), recorded at 412 dp width; Roborazzi `outputDir` = `app/src/test/screenshots`; baselines committed.
- [ ] T4.8 Accessibility pass: content descriptions on icon buttons, 48 dp touch targets where the token says 44 (minimum interactive size), TalkBack order.

## Phase 5: Release (gate G5)

- [ ] T5.1 R8 / resource shrinking for release; keep rules for Room and Media3.
- [ ] T5.2 `scripts/check_apk.sh` passes: size ≤ 25 MB, no INTERNET, permissions list matches `docs/SPEC.md`.
- [ ] T5.3 GitHub Actions workflow: build release APK on tag `v*`, sign with secrets `OOSCAM_KEYSTORE_B64`, `OOSCAM_KEY_ALIAS`, `OOSCAM_KEY_PASSWORD`, `OOSCAM_STORE_PASSWORD` (owner adds them), attach to a GitHub Release.
- [ ] T5.4 `README.md`: what it is, clean-room note, install from Releases, screenshots, privacy (no network), license.
- [ ] T5.5 `CHANGELOG.md` for 0.1.0.
- [ ] T5.6 Version 0.1.0 (versionCode 1).

## Needs owner (defaults the build uses)

| Item | Default | When |
| --- | --- | --- |
| Cloud environment network | Custom: Trusted defaults + `dl.google.com` | Before Phase 0 |
| Cloud environment variables | `BASH_DEFAULT_TIMEOUT_MS=600000`, `BASH_MAX_TIMEOUT_MS=600000` | Before Phase 0 |
| Cloud setup script | `bash scripts/setup_cloud.sh` | Before Phase 0 |
| Device for checks | Owner's Android 12+ phone | End of Phases 0, 2, 3, 4 |
| Phase 0 device checklist | Install debug APK; record 2 segments with screen off; report gap ms and Transformer time from `adb logcat -s OOSCamSpike` or the on-screen log | End of Phase 0 |
| Phase 2 device checklist | Install debug APK; (1) record 60 min at 1080p30 with the screen off, no crash, note battery drop; (2) stop from the notification Stop action; (3) with 5-min segments, check the gap between parts is at most 1 s; (4) fill storage below 500 MB and check the stop reason low_storage; (5) receive a phone call while recording and check it stops and the file plays; (6) pick an SD card folder and check files land there; (7) deny the mic: recording works without audio and a toast shows once; deny the camera: Record stays disabled; (8) deny notifications: recording still works and the banner shows | End of Phase 2 |
| Release signing | Owner creates a keystore and adds 4 GitHub secrets | Phase 5 |
| Package name | `io.github.ooscam` | Fixed |
| Red dot overlay | No (notification only) | Fixed |
| Import videos from outside OOS Cam | Yes, photo picker | Fixed |
| Phone call during recording | Stop and finalize | Fixed |
| Thermal SEVERE | Stop with reason thermal | Fixed |
| Google Play | Not in MVP | Fixed |

## Blockers

None.

## Log

- 2026-11-25: Build kit created (spec, tests, design, loop files).
- 2026-11-25: T0.1 LICENSE (Apache-2.0, fetched from apache.org, 202 lines) and NOTICE added; .kotlin/ added to .gitignore.
- 2026-11-25: T0.2 setup_cloud.sh ran (android-36, build-tools 36.0.0, 19 s); Gradle wrapper 9.9.2 generated; check_env.sh exit 0 when run with JAVA_TOOL_OPTIONS/_JAVA_OPTIONS unset (see DECISIONS).
- 2026-11-25: T0.3 version catalog (AGP 9.5.2, Kotlin 2.4.21, Gradle 9.9.2, versions read from maven-metadata.xml), settings with :core and :app, gradle.properties; `./gradlew projects` lists both modules. Maven Central answered 429 under parallel load; added Gradle repository retry/backoff system properties.
- 2026-11-25: T0.4 :app skeleton (MainActivity shows "OOS Cam", minSdk 31, compileSdk/targetSdk 36); assembleDebug lint testDebugUnitTest green; merged manifest and APK have no INTERNET. compose-bom pinned to 2026.06.01 and core-ktx to 1.18.0 because newer ones need compileSdk 37 and setup_cloud.sh installs android-36.
- 2026-11-25: T0.5 :core:runFixtures JavaExec (workingDir = repo root) reads both case files and writes empty export_actual.json / segment_actual.json; 4 JUnit 5 tests pass.
- 2026-11-25: T0.6 debug-only spike: SpikeActivity, SpikeCaptureService (camera|microphone FGS, Stop action, partial wake lock, 2 x 20 s segments cut on recorded duration, gap log), SpikeTransformerJob (2 clips, trim, 2x on a range, removeAudio). Pure logic (gap math, SegmentLoop, job parts) has 13 JVM tests in app/src/testDebug; a mutation check made 2 of them fail. Log tag OOSCamSpike. Not run on a device: that is an owner step. lifecycle pinned to 2.10.0 (2.11.x needs compileSdk 37).
- 2026-11-25: T0.7 docs/SPIKE.md written: measured clean assembleDebug 29.7 s, clean :core:test 4.4 s, clean assembleDebug lint 25.2 s (no build cache), plus the owner phone checklist.
- 2026-11-25: Phase 0 done; pull request https://github.com/izark99/OOS-CAM/pull/1 open into main.
- 2026-11-25: Phase 0 PR #1 merged into main (efce24f). Phase 1 started in the same session at the owner's request (PROMPT.md normally wants a new session).
- 2026-11-25: T1.1 :core models (EditProject, ProjectClip, SpeedRange, ExportPlan, PlanPart, RecordingInput, SegmentPlan) with strict kotlinx-serialization (speed read as exact decimal, nulls and defaults written); 6 tests.
- 2026-11-25: T1.2 planExport tier 1 (R1 order, R2/E6 trim validation, R3 trimmed sum, E1 clip count, R9 audio); 12 tests.
- 2026-11-25: T1.3 planExport tier 2 (R4 range clipping and W1, E7, E4, E3, R5b part splitting and equal-speed merge before rounding, R7 exact half-up rounding with BigDecimal); 19 tests.
- 2026-11-25: T1.4 planExport tier 3 (E2, E5 only without blocking errors, R9 audio, R10 export height and W2, R11 error shape); 12 tests.
- 2026-11-25: T1.5 planSegments (R12 bitrate presets, time and SD byte caps with ceiling, R13 low-storage end and tie rule, R14 file names); 20 tests.
- 2026-11-25: T1.6 runFixtures runs the real planners (strict fixture decoding); acceptance --suite core: base 20/20 and 9/9, v2 20/20 and 9/9. The T0.5 wiring tests that expected empty arrays were replaced by tests of the real output (the task said Phase 1 fills it); no test was loosened.
- 2026-11-25: T1.7 rule-level tests (all six speeds accepted, neighbours rejected, E5 skipped with E3/E6/E7, seeded export and segment invariants, part-number padding). Written after the code, so checked with three mutations (no merge, tie to low storage, round down): each made at least one test fail; code restored. 85 tests in :core.
- 2026-11-25: Phase 1 done; pull request https://github.com/izark99/OOS-CAM/pull/2 open into main.
- 2026-11-25: Phase 2 started (new session from merged main). Robolectric 4.18.1, androidx.test 1.7.0, Room 2.8.5, KSP 2.3.14 added; testLogging prints each test.
- 2026-11-25: T2.1 CaptureService (LifecycleService, FGS camera|microphone with mic type only when granted), CaptureStateMachine (first stop reason wins), ongoing notification with chronometer and Stop action, CaptureLauncher refuses to start unless the activity is visible and CAMERA is granted; core StopReason gains error, thermal, permission_revoked, phone_call; 16 Capture tests.
- 2026-11-25: T2.2 :core exposes segmentCapMs, segmentFileName, segmentBytesPerMs, isLowStorage and planSegments now calls them (acceptance core still 20/20, 9/9); RecorderController rolls to a new Recording when the open one reaches the core cap, names parts from core, finalizes on stop; CameraX engine (VideoCapture + Recorder, one Recording per segment) is the service default; 8 controller tests.
- 2026-11-25: T2.3 FreeSpaceSampler (sample at start then every 5 s, first sample below 500 MB via core isLowStorage stops with low_storage), ThermalMonitor (SEVERE or worse, tolerates a missing thermal service), CallMonitor (audio mode in-call/communication, TelephonyCallback only if READ_PHONE_STATE is granted); service starts and stops them; 9 tests (33 Capture tests).
- 2026-11-25: T2.4 CaptureWakeLock (partial, bounded 24 h, released when the last segment finalizes or the service is destroyed); ScreenOffController sets window brightness to BRIGHTNESS_OVERRIDE_OFF and clears FLAG_KEEP_SCREEN_ON so the system timeout turns the screen off, exit restores both; ScreenOffOverlay (theme scrim, tap to wake); 4 tests (37 Capture tests). The camera screen wires the button in T2.5.
