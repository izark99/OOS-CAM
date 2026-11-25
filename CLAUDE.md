# OOS Cam: notes for Claude Code

## Where things are

| Path | What |
| --- | --- |
| `PROMPT.md` | The build loop rules. Read first. |
| `GOALS.md` | One `/goal` per phase. |
| `PROGRESS.md` | STATUS line, tasks, owner defaults, blockers, log. |
| `docs/SPEC.md` | MVP specification. |
| `docs/DESIGN.md` | Layout numbers, component rules, states. |
| `docs/DECISIONS.md` | Decisions made during the build, one line each. |
| `design/design-system/oos-cam/` | `MASTER.md`, `tokens.css`, `tokens.json`: the only source of colors, type, spacing, radii. |
| `design/screens/` | 11 target screens as HTML + PNG (412 px wide). |
| `design/generate_screens.py`, `design/audit_screens.py` | How the mock-ups were made and checked. |
| `testcase/` | Acceptance fixtures, oracle, expected output. READ-ONLY. |
| `scripts/` | `setup_cloud.sh`, `check_env.sh`, `check.sh`, `check_tokens.sh`, `check_screens.py`, `check_apk.sh`. |
| `core/`, `app/` | Gradle modules (created in Phase 0). |

## Commands

- `scripts/check.sh N`: all gates G0..GN.
- `./gradlew :core:test`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug lint`.
- `./gradlew -q :core:runFixtures --args="<fixturesDir> <outDir>"`.
- `python3 testcase/run_acceptance.py --suite core|all`.
- `./gradlew recordRoborazziDebug` (only when a screen intentionally changes; say so in the PR), `./gradlew verifyRoborazziDebug`.

## Rules that never change

- One task per iteration, tests first, commit and push after each green task.
- Never edit `testcase/`, never weaken a test or gate, no fixture values in code.
- No INTERNET permission. Recording always shows a notification with Stop.
- Clean-room: no code, strings, icons or assets from other camera apps; no Apple icons.
- Colors and sizes only through `Tokens.kt` generated from `tokens.json`.
- Each phase ends in a pull request into `main`, never a direct merge.

## Cloud session notes

- VM: Ubuntu 24.04 x86_64, 4 vCPU, 16 GB RAM, 30 GB disk, OpenJDK 21, Gradle, Python 3. No emulator: never try to start one.
- Push after every commit; the VM can be reclaimed when idle.
- Docker and PostgreSQL are installed but not running. This project needs neither.
- Network: only allowlisted hosts. Needed: `dl.google.com` (owner adds it), `maven.google.com`, `repo.maven.apache.org`, `repo1.maven.org`, `services.gradle.org`, `plugins.gradle.org`, `github.com`. If a host is blocked, record it in Blockers and stop. Never use mirrors.
- Commands time out after 2 min by default (10 min max). Run long Gradle builds with `--console=plain` and a 600000 ms timeout, or in the background and poll.
- `local.properties` is written by `scripts/setup_cloud.sh` and is git-ignored.

## Conventions

- Kotlin official style; `ktlint` via Gradle if added, never reformat unrelated files.
- Commit messages: `T<phase>.<n>: <summary>`; PR titles: `Phase N: <name>`.
- Test names describe the rule: `planExport_rangeTouchingTrimEdge_isDroppedWithW1`.
- All file content, code, UI strings (English) and docs in English; Vietnamese only in `values-vi/strings.xml`.
