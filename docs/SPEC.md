# OOS Cam MVP Specification

Source of truth for the build. The editable original is the owner's "OOS Cam MVP Specification" doc; this file is its markdown copy (2026-11-25).

## Summary

OOS Cam is an open-source Android camera that records video with the screen off, then trims, joins, speeds up and mutes clips on the device. Its capture features are a clean-room rebuild, from a public store listing only, of a paid background-recording camera app; the editor and UI are new.

| Decision | Value |
| --- | --- |
| Feature source | Clean-room: public listing only, no decompiling, no reused code, assets, strings or name |
| Platform | Android 12+ (API 31), phones, portrait only |
| Capture | Screen-off recording in a foreground service (type camera and microphone), visible notification with Stop |
| Segments | Auto-split every 5 / 10 / 30 / 60 min, default 30; stop when free space < 500 MB |
| Quality | Default 1080p30; up to 4K30 when the camera supports it |
| Audio | Mic on by default, toggle on the camera screen |
| Editor | Trim, join, speed 0.25x to 4x, mute; max 10 clips, 30 min total; export MP4 (H.264 / AAC) up to 1080p |
| UI | Glass-style look inspired by recent iOS (blur, floating controls, large radii); no Apple assets or icons |
| Languages | English (default) and Vietnamese |
| Stack | Kotlin, Jetpack Compose, CameraX, Media3 Transformer, Room |
| Storage | MediaStore Movies/OOS Cam on device; SD card through a folder the user picks |
| License and release | Apache-2.0, APK on GitHub Releases |
| Build | Claude Code cloud sessions; dl.google.com added to the environment allowlist |
| Real-device test | Owner, at the end of each phase |

## Scope

**In scope**

- Record video with the screen off or the app in the background, until the user taps Stop
- Long recording split into segment files (5 / 10 / 30 / 60 min), no gaps longer than 1 s between segments
- Rear and front camera, resolution and fps picker limited to what the device reports, torch on/off
- Mic toggle; storage target: device or an SD card folder; low-storage auto-stop
- Library of OOS Cam videos (grid, play, share, delete with confirm, select many)
- Editor: join up to 10 clips, trim each clip, speed per range (0.25x to 4x), mute; export MP4
- Settings screen; English and Vietnamese strings
- Glass-style UI with a solid fallback when blur is off (battery saver, "Reduce glass effects")

**Out of scope (MVP)**

- Photo mode, RAW, manual ISO / shutter / focus, HDR, night mode
- Filters, LUTs, text overlays, music, transitions
- Any network feature, account, analytics or ads
- Hidden recording without a notification (Android does not allow it, and Play policy forbids it)
- Starting recording on boot, from a widget or from the lock screen
- Tablets, landscape layouts, iOS build

## Architecture

Everything runs on the phone; there is no server.

```
Compose UI + view models (Camera, Library, Editor, Export, Settings)
        |                                   |
  CaptureService  --->  :core  <---  ExportEngine
  (FGS camera|mic,     planSegments,  (Media3 Transformer
   CameraX VideoCapture) planExport,   Composition from the
        |               file names,    ExportPlan)
        |               validation          |
        v               (pure Kotlin)       v
  Local storage: Room (sessions, segments, projects) + MediaStore Movies/OOS Cam or SD folder
```

Capture and export only execute plans that `:core` computes; the Library screen reads Room and MediaStore directly.

| Constraint | What it forces | Source |
| --- | --- | --- |
| Android 14+: a camera foreground service must declare type camera and FOREGROUND_SERVICE_CAMERA | Manifest declares `camera\|microphone`; recording always shows a notification | [FGS types](https://developer.android.com/develop/background-work/services/fgs/service-types) |
| Android 12+: no foreground service start from the background | Recording starts only from a tap in the visible app (or the notification) | [FGS changes](https://developer.android.com/develop/background-work/services/fgs/changes) |
| Android 15+: no camera service from BOOT_COMPLETED | No auto-start on boot | [Android 15 FGS](https://developer.android.com/about/versions/15/changes/foreground-service-types) |
| Cloud VM: Ubuntu 24.04, JDK 21, Gradle, 4 vCPU, 16 GB RAM, 30 GB disk | Gradle daemon heap 4 GB, one build at a time | [Cloud environments](https://code.claude.com/docs/en/cloud-environments) |
| Cloud allowlist has maven.google.com and gradle.org, not dl.google.com | Owner adds dl.google.com; `scripts/setup_cloud.sh` installs SDK platform and build-tools | [Cloud environments](https://code.claude.com/docs/en/cloud-environments) |
| Cloud VM has no emulator | Gates use JVM tests, Robolectric and Roborazzi; real camera checked by the owner | [Cloud environments](https://code.claude.com/docs/en/cloud-environments) |
| Command timeout 2 min default, 10 min max | Set BASH_DEFAULT_TIMEOUT_MS=600000; long builds in the background | [Cloud environments](https://code.claude.com/docs/en/cloud-environments) |

## Data model and file rules

Video files live in MediaStore (or the chosen SD folder); Room holds only metadata, so deleting the app database never deletes a video.

| Entity | Fields | Notes |
| --- | --- | --- |
| RecordingSession | id, startedAt, lens (back / front), width, height, fps, audioOn, segmentMinutes, target (device / sd), stopReason | stopReason: user, low_storage, error, thermal, permission_revoked, phone_call |
| Segment | id, sessionId, index, uri, startOffsetMs, durationMs, bytes | index starts at 1 |
| EditProject | id, name, createdAt, updatedAt, muted, exportHeight | exportHeight: 720 or 1080 |
| ProjectClip | projectId, order, sourceUri, sourceDurationMs, sourceHeight, inMs, outMs | order 0..9 |
| SpeedRange | projectId, clipOrder, startMs, endMs, speed | times in source time of that clip |

1. Segment name: `OOSCam_YYYYMMDD_HHMMSS_partNN.mp4`, time = session start in local time, NN = index with at least 2 digits.
2. Export name: `OOSCam_edit_YYYYMMDD_HHMMSS.mp4`, time = export start.
3. A new segment starts when the current one reaches segmentMinutes, or 3,900 MB on an SD target (FAT32 limit is 4 GB).
4. Free space is checked every 5 s; below 500 MB the session finalizes the open segment and stops with `low_storage`.
5. A segment is valid only if its MP4 is finalized (moov atom written); an unfinalized file is listed as "Damaged".
6. Room schema versions are exported to `app/schemas/`; every change needs a migration test.

## Feature catalog

P = parity with the original listing, N = new in OOS Cam.

| ID | Screen | Feature | Type |
| --- | --- | --- | --- |
| C1 | Camera | Big record button; recording keeps going when the screen turns off or the app goes to the background | P |
| C2 | Camera | Recording timer, segment counter, free space left | N |
| C3 | Camera | Lens switch, torch, mic toggle, quality chip (720p / 1080p / 4K, 30 fps) | N |
| C4 | Camera | "Screen off" button: dims to black overlay, then lets the system turn the screen off | P |
| C5 | Notification | Ongoing notification with elapsed time and a Stop action | P |
| C6 | Camera | Permission sheet for camera, mic, notifications, with reason text | N |
| L1 | Library | Grid grouped by session, segment parts shown as one stack | N |
| L2 | Library | Play, share, delete (confirm), multi-select, "Edit selected" | N |
| L3 | Library | Damaged-file badge and a "Delete damaged" action | N |
| E1 | Editor | Timeline of up to 10 clips, drag to reorder | N |
| E2 | Editor | Trim handles per clip, 0.1 s precision | N |
| E3 | Editor | Speed range on a clip: 0.25x, 0.5x, 1x, 1.5x, 2x, 4x | N |
| E4 | Editor | Mute whole project | N |
| E5 | Export | Export sheet: 720p or 1080p, progress, cancel, open result | N |
| S1 | Settings | Segment length 5 / 10 / 30 / 60 min (default 30) | P |
| S2 | Settings | Storage target: device or SD card folder | P |
| S3 | Settings | Default quality, default mic, language, reduce glass effects | N |
| S4 | Settings | About: version, license, source link | N |

Offline and no ads (parity) are rules, not screens: the manifest has no INTERNET permission.

## Edit and segment rules

`planExport(project)` and `planSegments(recording)` in `:core` carry all logic the tests check. The exact rules R1–R14, error and warning codes and the JSON shapes are in `testcase/TEST_RULES.md`, which wins over this summary.

- Tier 1: order by clip order; each trimmed clip ≥ 500 ms inside its source (E6); output = sum of trimmed lengths.
- Tier 2: ranges clipped to the trim window (outside → dropped, W1); no overlap (E3); speeds 0.25, 0.5, 1, 1.5, 2, 4 (E4); empty range (E7); equal-speed neighbours merge; part length = round half up of length ÷ speed.
- Tier 3: ≤ 10 clips (E1), trimmed ≤ 1,800,000 ms (E2), output ≤ 1,800,000 ms (E5), exactly on a limit passes; muted drops audio; 1080 export drops to 720 with W2 if any source is below 1080.
- Segments: bitrate presets 8 / 16 / 45 Mbps (+128 kbps audio); time cap and SD size cap 3,900 MB; end = earliest of user stop and first free-space sample below 500 MB (tie → user).

## Permissions matrix

INTERNET is never declared.

| Permission | Needed for | When asked | If denied |
| --- | --- | --- | --- |
| CAMERA | C1, C3 | First tap on Record | Permission sheet; Record disabled |
| RECORD_AUDIO | Mic on | First tap on Record with mic on | Records with mic off, toast once |
| POST_NOTIFICATIONS (13+) | C5 | First tap on Record | Recording still works; banner explains the notification may be hidden |
| FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_MICROPHONE | Background capture | Install time | Not deniable |
| WAKE_LOCK | Capture with the screen off | Install time | Not deniable |
| SAF tree URI | S2 SD card target | When the user picks SD | Falls back to device storage |
| Photo picker (READ_MEDIA_VIDEO only as fallback) | Import clips not made by OOS Cam | First tap on Import | Editor uses OOS Cam clips only |

## Limits and performance

| Item | Limit or target |
| --- | --- |
| Bitrate presets for planning | 720p30 8 Mbps, 1080p30 16 Mbps, 2160p30 45 Mbps, audio 128 kbps AAC |
| Segment gap | ≤ 1,000 ms of lost footage between two segments |
| Continuous recording | 60 min at 1080p30 with the screen off, no crash, battery drop logged |
| Editor | 10 clips, 1,800,000 ms trimmed source, 1,800,000 ms output |
| Export speed target | 1 min of 1080p output in ≤ 90 s on the owner's phone |
| Cold start to camera preview | ≤ 1.5 s on the owner's phone |
| APK size | ≤ 25 MB release APK |
| JVM test suite | ≤ 5 min on the cloud VM |

Phase 0 spike measures: CameraX recording inside a foreground service with the screen off (Android 12 and 14+), the gap between segments, a Media3 Transformer job (join, trim, 2x, mute), glass blur frame time (≤ 16 ms), and cloud build times.

## Acceptance use case

1. Open OOS Cam, grant camera, mic and notifications, keep 1080p30 and 30-min segments.
2. Record about 95 s with the screen off, stop from the notification. Clip A = 95,000 ms.
3. Record about 62.5 s. Clip B = 62,500 ms.
4. Library: select A and B, "Edit selected".
5. Trim A to 2,000–92,000 ms and B to 1,500–61,500 ms.
6. On A, 2x on 30,000–60,000 ms.
7. Mute, export at 1080p.

Expected: parts A 2,000–30,000 @1x (28,000), A 30,000–60,000 @2x (15,000), A 60,000–92,000 @1x (32,000), B 1,500–61,500 @1x (60,000); output 135,000 ms (± 100 ms on the device file); no audio track; `OOSCam_edit_*.mp4` in Movies/OOS Cam. On the JVM this is fixture X01.

## Delivery phases

| Phase | Gate | Commands |
| --- | --- | --- |
| 0 Spike + scaffold | G0 | `scripts/check_env.sh`, `./gradlew assembleDebug lint` |
| 1 Core logic | G1 | `./gradlew :core:test`, `python3 testcase/run_acceptance.py --suite core` |
| 2 Capture | G2 | `./gradlew :app:testDebugUnitTest --tests '*Capture*'` |
| 3 Library + editor | G3 | `./gradlew testDebugUnitTest`, `python3 testcase/run_acceptance.py --suite all` |
| 4 Glass UI + i18n | G4 | `./gradlew verifyRoborazziDebug`, `python3 scripts/check_screens.py`, `scripts/check_tokens.sh` |
| 5 Release | G5 | `./gradlew assembleRelease`, `scripts/check_apk.sh` |

Every gate re-runs all earlier gates (`scripts/check.sh N`). Each phase ends in a pull request into `main`.

## Decided items

| Question | Decision |
| --- | --- |
| Package name | `io.github.ooscam` |
| Import videos not recorded by OOS Cam | Yes, through the system photo picker |
| Red dot overlay while recording | No: the notification is the only indicator |
| Phone call during recording | Stop and finalize the segment (stopReason phone_call) |
| Thermal status SEVERE or higher | Stop, stopReason thermal |
| Google Play | Not in the MVP |
