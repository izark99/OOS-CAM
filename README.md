# OOS Cam

Open-source Android camera that keeps recording with the screen off, splits long recordings into safe segments, and trims, joins, speeds up and mutes clips on the phone. No internet permission, no ads, no account.

Status: build kit. The app is built phase by phase by Claude Code cloud sessions; see `PROGRESS.md`.

- Spec: `docs/SPEC.md` · Design: `docs/DESIGN.md` and `design/screens/` · Tests: `testcase/TEST_RULES.md`
- How the build runs: `PROMPT.md` (loop rules) and `GOALS.md` (one goal per phase)

Clean-room note: the capture feature set follows the public store description of an existing background-recording app. No code, strings, icons or assets of any other app are used.

License: Apache-2.0 (added in Phase 0).
