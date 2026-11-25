# Phase 0 spike

Two parts: build times measured in the cloud VM, and a checklist the owner runs on a phone with the debug APK.
The spike code lives only in `app/src/debug/` and is thrown away; Phase 2 builds `CaptureService` fresh.

## 1. Build times in the cloud VM

VM: 4 vCPU, 15 GB RAM, OpenJDK 21, Gradle 9.9.2, AGP 9.5.2, Kotlin 2.4.21. Dependencies were already in the local Gradle cache, so these numbers have no download time. All runs used `--no-build-cache` and `clean`, so `org.gradle.caching=true` did not help them. The daemon was stopped before the first run (cold daemon). Seconds are the wall time of the command.

| Command | Wall time | Gradle tasks |
| --- | --- | --- |
| `./gradlew clean assembleDebug --no-build-cache` (cold daemon) | 29.7 s | 38 executed |
| `./gradlew clean :core:test --no-build-cache` | 4.4 s | 5 executed |
| `./gradlew clean testDebugUnitTest --no-build-cache` | 5.8 s | 26 executed |
| `./gradlew clean assembleDebug lint --no-build-cache` (gate G0 command) | 25.2 s | 47 executed |

First green `assembleDebug lint` on this VM, with the dependency downloads, before the spike libraries were added: 42 s. Maven Central answered HTTP 429 (too many requests) several times while the first downloads ran in parallel; `gradle.properties` now sets repository retries with a back-off, and a plain re-run also worked.

Targets from `docs/SPEC.md`: JVM test suite <= 5 min. Today's suites are tiny, so this is not yet a real measurement; measure again at the end of Phase 1.

Debug APK size: 42,023,786 bytes (debug, not shrunk, includes CameraX and Media3). The 25 MB limit is for the release APK; check it at T5.1 with R8 on.

## 2. What the spike does

- `SpikeActivity` (launcher icon "OOS Spike", debug only) asks for camera, microphone and notification permission and has three buttons: Record 2 x 20 s, Stop, Run Transformer job. It shows a log list.
- `SpikeCaptureService` is a foreground service of type camera|microphone. The notification has a Stop action. It records 2 segments back to back with CameraX `Recorder`, cuts each at 20 s of recorded footage, and holds a partial wake lock (5 min timeout). The next segment starts only after the previous one finalized (the always-legal baseline), so the gap number is an honest worst case for this approach.
- `SpikeTransformerJob` joins the 2 segments with Media3 Transformer: clip 1 trimmed 2.0 to 12.0 s with 2x on 6.0 to 10.0 s, clip 2 trimmed 1.0 to 9.0 s, audio removed. Expected output length 16,000 ms. It logs the elapsed time.
- Segment files are written to the app's own external files folder `spike/`, so no storage permission is needed.
- Log tag: `OOSCamSpike`. The same lines show on screen.

## 3. Owner checklist (phone, Android 12 or newer)

Install the debug APK: `app/build/outputs/apk/debug/app-debug.apk` (build it with `./gradlew assembleDebug`, or download it from the pull request if attached).

Read the log with `adb logcat -s OOSCamSpike` or on the spike screen.

1. Open "OOS Spike". Tap Record 2 x 20 s. Allow camera, microphone and notifications. Expect a notification with a Stop button.
2. Press the power button to turn the screen off. Wait about 50 seconds. Turn the screen on.
3. In the log find the line `event=segment_start segment=2 gap_ms=<N>`. Write down N. Target: N <= 1000 (SPEC: at most 1,000 ms of lost footage).
4. Find the two `event=segment_finalize` lines. Both must show `error=0` and `recorded_ms` close to 20000.
5. Repeat steps 1 to 4 once with the app sent to the background (Home button) and the screen on. Write down the gap.
6. Repeat on a second phone with Android 14 or newer if you have one (foreground service rules are stricter there). Write down the Android version and phone model next to each result.
7. Tap Stop in the notification during a recording. The notification must disappear and the log must show `event=service_finished`.
8. Tap Run Transformer job after two segments exist. Find `event=transform_done elapsed_ms=<T>`. Write down T. Target for planning: 1 min of 1080p output in <= 90 s; this job makes 16 s of output, so T <= 24,000 ms would be on target.
9. Pull the result and check it by eye: `adb pull /sdcard/Android/data/io.github.ooscam/files/spike/spike_export.mp4`. It should be about 16 s, with no sound, and the middle part should play twice as fast.
10. Send back: Android version, phone model, gap_ms per run, T, any line that contains `ERROR`, and whether the recording survived the screen off.

If a step fails, copy the last 30 lines of `adb logcat -s OOSCamSpike` into the pull request.

## 4. Not measured by this spike

- Glass blur frame time (SPEC: <= 16 ms). The spike has no blur screen. Measure it in Phase 4 (T4.2) on the owner's phone.
- 60 minutes of continuous recording, battery drop and thermal behaviour. Phase 2.
- Back-to-back segments without waiting for finalize (CameraX may allow starting the next recording early). If the gap in step 3 is above 1000 ms, Phase 2 should test that option first.

## 5. Findings to carry forward

- Media3 pulls `android.permission.ACCESS_NETWORK_STATE` into the merged manifest. It is not INTERNET, but the Phase 5 check that the permission list matches `docs/SPEC.md` will fail unless it is removed with `tools:node="remove"` or the SPEC lists it.
- `SpeedChangeEffect(float)` and `EditedMediaItemSequence.Builder(List)` are deprecated in Media3 1.11. The spike uses the sequence builder with track types; Phase 3 should use a `SpeedProvider` instead of the float constructor.
- Several Media3 classes are `@UnstableApi`. Use `androidx.annotation.OptIn`; the Kotlin `OptIn` does not satisfy lint.
- Library versions are capped by compileSdk 36: compose-bom 2026.06.01, core-ktx 1.18.0, lifecycle 2.10.0. Newer ones need compileSdk 37, which `scripts/setup_cloud.sh` does not install.
