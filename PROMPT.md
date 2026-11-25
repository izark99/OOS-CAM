# OOS Cam build loop

You are building OOS Cam, an open-source Android camera that records video with the screen off and edits clips on the device. Read `CLAUDE.md`, `docs/SPEC.md`, `docs/DESIGN.md`, `testcase/TEST_RULES.md` and `PROGRESS.md` before the first task of a session.

## Every iteration

1. Read `PROGRESS.md`. Pick the FIRST unticked task of the current phase. Do one task per iteration.
2. Write or extend the tests for that task first. Run them and see them fail for the right reason.
3. Write the code until those tests pass.
4. Run the gate commands of the current phase and of all earlier phases (`scripts/check.sh <N>`).
5. If green: tick the task in `PROGRESS.md`, add one Log line (date, task id, what changed), commit with message `T<phase>.<n>: <summary>`, push the branch.
6. Print the Evidence block (below) as the last thing in the turn.

## Fixed stack (do not change without a BLOCKED note)

- Kotlin, Gradle Kotlin DSL with a version catalog, JDK 21 toolchain (cloud VM has OpenJDK 21).
- Modules: `:core` (pure Kotlin/JVM, kotlinx-serialization, no Android imports) and `:app` (Android, Jetpack Compose, Material 3 only as a base, custom components on top).
- CameraX `VideoCapture` + `Recorder` inside a `LifecycleService` started as a foreground service of type `camera|microphone`.
- Media3 Transformer (`Composition`, `EditedMediaItem`, `SpeedChangeEffect`/speed provider, `removeAudio`) for export.
- Room for metadata, MediaStore and Storage Access Framework for files.
- Tests: JUnit 5 in `:core`; JUnit 4 + Robolectric + Roborazzi in `:app`.
- minSdk 31, targetSdk = compileSdk = the newest stable platform installed by `scripts/setup_cloud.sh`.
- Package `io.github.ooscam`. No `INTERNET` permission, no analytics, no ads, no network library.

## Hard rules (anti-cheat)

- Never edit anything under `testcase/`. If a rule there looks wrong, add a BLOCKED entry and stop.
- No fixture values in production code: no case ids, no expected durations, no file names from `testcase/`. The planners compute everything from input.
- Never weaken, skip, `@Ignore`, delete or loosen a test or a gate to make it pass. Never change a gate command in this file, `GOALS.md` or `scripts/`.
- Colors, sizes, radii and type come only from `design/design-system/oos-cam/tokens.json` through the generated `ui/theme/Tokens.kt`. `scripts/check_tokens.sh` enforces it.
- Clean-room: never download, decompile or copy code, strings, icons or assets of any other camera app. No Apple icons or SF Symbols.
- Recording always shows the foreground-service notification with a Stop action. Never add a way to hide it.
- Print real command output in the Evidence block. Never write "tests pass" without the command and its exit code.

## Advisor (only at these points)

Call the advisor: at the start of each phase (plan check), before a hard-to-undo choice (module boundaries, Room schema, service lifecycle, Transformer composition shape), after two failed attempts at the same task, and before declaring a phase done. Not on every task.

## Blocked

Stop and write `STATUS: BLOCKED` in `PROGRESS.md` with a Blockers entry when: a needed host is not reachable (record the host, do not look for mirrors), a test in `testcase/` contradicts `docs/SPEC.md`, a task needs a real device, or the same task failed 3 times. Then print the Evidence block and end the turn.

## Phase pull request

When the last task of a phase is ticked and its gate passes: push the branch, open a pull request into `main` titled `Phase N: <name>` using `.github/pull_request_template.md`, filled with real command output only. Print its URL in the Evidence block, set `STATUS: PHASE N DONE, PR OPEN` in `PROGRESS.md`, commit, push, then stop. Do not start the next phase in the same session.

## Gates

| Phase | Gate | Commands (all must exit 0, plus every earlier gate) |
| --- | --- | --- |
| 0 Spike + scaffold | G0 | `scripts/check_env.sh` and `./gradlew assembleDebug lint` |
| 1 Core logic | G1 | `./gradlew :core:test` and `python3 testcase/run_acceptance.py --suite core` |
| 2 Capture | G2 | `./gradlew :app:testDebugUnitTest --tests '*Capture*'` |
| 3 Library + editor | G3 | `./gradlew testDebugUnitTest` and `python3 testcase/run_acceptance.py --suite all` |
| 4 Glass UI + i18n | G4 | `./gradlew verifyRoborazziDebug`, `python3 scripts/check_screens.py` and `scripts/check_tokens.sh` |
| 5 Release | G5 | `./gradlew assembleRelease` and `scripts/check_apk.sh` |

`scripts/check.sh N` runs G0 to GN in order and stops at the first failure.

## Evidence block (end of every turn)

```
EVIDENCE
phase: <N> task: <id> status: <done|in progress|BLOCKED|PHASE DONE>
$ <command>
<last 15 lines of real output>
exit: <code>
(repeat for each gate command run this turn)
git: <short sha> pushed to <branch>
pr: <url or none>
next: <next task id or "stop">
```
