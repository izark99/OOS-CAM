# Decisions

One line per decision made during the build: date, phase, decision, reason.

- 2026-11-25, kit: clean-room feature list from the public store listing only; no decompiling.
- 2026-11-25, kit: rounding half up on ms; equal-speed parts merge; tie between user stop and low storage = user.
- 2026-11-25, kit: no red-dot overlay; the foreground notification is the only recording indicator.
- 2026-11-25, kit: dark theme only for the MVP; new OOS Cam design system (Liquid Glass style).
- 2026-11-25, phase 0: gradle wrapper is created in T0.2 (check_env.sh requires gradlew); gate G0 is first fully green at T0.4, so T0.1-T0.3 are ticked on their own checks. Gates were not edited.
- 2026-11-25, phase 0: this cloud VM sets JAVA_TOOL_OPTIONS (sandbox proxy), so `java -version` prints a 'Picked up' line first and scripts/check_env.sh misreads the JDK; run it as `env -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS scripts/check_env.sh`. The script was not edited.
- 2026-11-25, phase 0: compileSdk=targetSdk=36 (newest platform installed by setup_cloud.sh); compose-bom 2026.06.01 and core-ktx 1.18.0 are the newest releases that still compile against 36.
