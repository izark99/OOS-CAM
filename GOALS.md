# Goals, one per phase

Start each phase in a NEW cloud session from the freshly merged `main`. Paste one goal. Each is under 4,000 characters.

## Phase 0: Spike + scaffold

```
/goal Follow PROMPT.md for Phase 0 of PROGRESS.md (tasks T0.1 to T0.7). The goal is met only when ALL of these are shown in this conversation as real command output: (1) `scripts/check_env.sh` exits 0; (2) `./gradlew assembleDebug lint` exits 0; (3) every Phase 0 task in PROGRESS.md is ticked; (4) a pull request into main titled "Phase 0: Spike + scaffold" is open and its URL is printed in the Evidence block. Constraints: nothing under testcase/ is modified (show `git diff --stat main -- testcase/` empty); no INTERNET permission in any manifest. Each turn ends with the Evidence block from PROMPT.md. If PROGRESS.md says STATUS: BLOCKED, the goal is met as blocked: print the blocker and stop. Stop after 40 turns.
```

## Phase 1: Core logic

```
/goal Follow PROMPT.md for Phase 1 of PROGRESS.md (tasks T1.1 to T1.7). The goal is met only when ALL of these are shown in this conversation as real command output: (1) `./gradlew :core:test` exits 0; (2) `python3 testcase/run_acceptance.py --suite core` exits 0 and prints "RESULT: PASS" with base and v2 at 20/20 export and 9/9 segment; (3) `scripts/check.sh 1` exits 0; (4) every Phase 1 task is ticked; (5) a pull request into main titled "Phase 1: Core logic" is open and its URL is printed. Constraints: `git diff --stat main -- testcase/` is empty; `grep -rnE "X0[0-9]|X1[0-9]|S0[0-9]|135000|135_000" core/src/main` finds nothing. Each turn ends with the Evidence block. If PROGRESS.md says STATUS: BLOCKED, the goal is met as blocked: print the blocker and stop. Stop after 50 turns.
```

## Phase 2: Capture

```
/goal Follow PROMPT.md for Phase 2 of PROGRESS.md (tasks T2.1 to T2.8). The goal is met only when ALL of these are shown in this conversation as real command output: (1) `./gradlew :app:testDebugUnitTest --tests '*Capture*'` exits 0 with at least 15 tests run; (2) `scripts/check.sh 2` exits 0; (3) `./gradlew assembleDebug` exits 0 and the APK path is printed; (4) every Phase 2 task is ticked; (5) a pull request into main titled "Phase 2: Capture" is open, its body lists the owner's device checks from PROGRESS.md, and its URL is printed. Constraints: testcase/ unchanged; the merged manifest (`app/build/intermediates/merged_manifest/debug/**/AndroidManifest.xml`) declares foregroundServiceType camera|microphone and has no INTERNET permission. Each turn ends with the Evidence block. If STATUS: BLOCKED, print the blocker and stop. Stop after 60 turns.
```

## Phase 3: Library + editor

```
/goal Follow PROMPT.md for Phase 3 of PROGRESS.md (tasks T3.1 to T3.8). The goal is met only when ALL of these are shown in this conversation as real command output: (1) `./gradlew testDebugUnitTest` exits 0; (2) `python3 testcase/run_acceptance.py --suite all` exits 0 and prints "RESULT: PASS" including "base/app-acceptance: 1/1 match"; (3) `scripts/check.sh 3` exits 0; (4) every Phase 3 task is ticked; (5) a pull request into main titled "Phase 3: Library + editor" is open, its How to review section has the acceptance use case steps for the owner's phone, and its URL is printed. Constraints: testcase/ unchanged; the export path builds its Media3 Composition only from the ExportPlan returned by :core. Each turn ends with the Evidence block. If STATUS: BLOCKED, print the blocker and stop. Stop after 60 turns.
```

## Phase 4: Glass UI + i18n

```
/goal Follow PROMPT.md for Phase 4 of PROGRESS.md (tasks T4.1 to T4.8). The goal is met only when ALL of these are shown in this conversation as real command output: (1) `./gradlew verifyRoborazziDebug` exits 0; (2) `python3 scripts/check_screens.py` exits 0 and lists 11 screens; (3) `scripts/check_tokens.sh` exits 0; (4) `scripts/check.sh 4` exits 0; (5) every Phase 4 task is ticked; (6) a pull request into main titled "Phase 4: Glass UI + i18n" is open with the 11 Roborazzi PNGs attached or linked next to design/screens/*.png, and its URL is printed. Constraints: testcase/ unchanged; every string in values/strings.xml has a values-vi/strings.xml entry (lint MissingTranslation is an error). Each turn ends with the Evidence block. If STATUS: BLOCKED, print the blocker and stop. Stop after 60 turns.
```

## Phase 5: Release

```
/goal Follow PROMPT.md for Phase 5 of PROGRESS.md (tasks T5.1 to T5.6). The goal is met only when ALL of these are shown in this conversation as real command output: (1) `./gradlew assembleRelease` exits 0; (2) `scripts/check_apk.sh` exits 0 and prints the APK size under 25 MB and "INTERNET: absent"; (3) `scripts/check.sh 5` exits 0; (4) every Phase 5 task is ticked; (5) a pull request into main titled "Phase 5: Release" is open and its URL is printed. Constraints: testcase/ unchanged; no signing key, keystore or password is committed (`git ls-files | grep -Ei "jks|keystore"` finds nothing). Each turn ends with the Evidence block. If STATUS: BLOCKED, print the blocker and stop. Stop after 40 turns.
```
