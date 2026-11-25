# OOS Cam design rules for the build

The ONLY source of tokens is `design/design-system/oos-cam/tokens.json` (mirror of `tokens.css`), explained in `design/design-system/oos-cam/MASTER.md`. Generate `app/src/main/java/io/github/ooscam/ui/theme/Tokens.kt` from it; never type a color, radius or size literal in a composable. Each token becomes a camelCase Kotlin name (`color-bg-camera` → `colorBgCamera`); `scripts/check_tokens.sh` checks all non-demo tokens exist. 1 CSS px in the mock-ups = 1 dp.

Target screens: `design/screens/*.png` (412 dp wide, 2x). Each has an HTML source made by `design/generate_screens.py`; measurements below come from `design/audit_screens.py` (`design/audit.json`).

## Screens

| File | Screen | Notes |
| --- | --- | --- |
| 01-camera-idle | Camera, idle | Black background, preview edge to edge behind controls |
| 02-camera-recording | Camera, recording | Recording pill (dot, timer, part, free space), "Screen off" glass button |
| 03-screen-off | Screen-off countdown | Black, dot + timer, 3 s countdown text, "Keep screen on" |
| 04-permissions | Permission sheet | Over the camera; Continue (accent), Not now (plain) |
| 05-library | Library | Session headers, 3-column square grid, parts badge, damaged tile |
| 06-library-select | Library, select | Round checkboxes, accent outline on picked, floating glass action bar |
| 07-editor | Editor | 16:9 preview, time row, timeline with trim handles and speed range, glass toolbar |
| 08-editor-speed | Speed sheet | Segmented speeds, range panel, Apply / Remove range |
| 09-export | Export sheet | Resolution dropdown, summary line, progress, Cancel |
| 10-settings | Settings | Grouped cards: Recording, Storage, Display, About |
| 11-components-states | Components and states | Every component and empty / error / progress / confirm / open dropdown / skeleton / toast |

## Layout numbers (dp)

| Item | Value |
| --- | --- |
| Top inset (status bar area) | 24 |
| Top bar | 56 high, starts at y 24, side padding 8 |
| Large page title (Library, Settings, Components) | display 28/34, top at y 80, padding 8 / 16 / 16 |
| First content block under a page title | y 138 |
| Editor preview | y 80, 16:9, side margin 16, radius 16 |
| Camera top controls | y 32, side 16, 44 high glass pills |
| Recording pill | y 88, centred, 44 high |
| Camera bottom area | 200 high, black; shutter 76; side buttons 44; bottom padding 32 |
| Floating bars (library select, editor toolbar) | 64 high, 8 from sides and bottom, pill radius |
| Sheets | 8 from sides and bottom, radius 24, padding 24 / 16 / 16, gap 16 |
| List rows | min 56, padding 16, divider 1 |
| Library grid | 3 columns, 2 gap, square tiles (136 at 412 wide, 119 at 360) |
| Timeline | 88 high, clip blocks 56, trim handles 14 wide, 22 padding left on the selected clip |

## Component rules

- Buttons: two heights only, 44 (md) and 56 (lg); pill radius; label 14/600 (md) or 16/600 (lg). Kinds: accent, glass, raised, plain, plain-danger, danger. One accent-filled control per app screen.
- Icon buttons 44×44; touch targets in Compose ≥ 48 dp (use `minimumInteractiveComponentSize`).
- Shutter 76 is the only other control size.
- Custom components (no Material defaults visible): segmented control, dropdown (anchored menu, 44 rows), round checkbox (24), switch (52×32), search field (44), sheet, toast (44 pill), progress (6), skeleton (keeps tile and row sizes), badge (24), clip chip, trim handle.
- Glass: `glass` fill + blur 24 + 1 dp `glass-border`; only on controls and bars, never on content. Fallback `glass-solid` when blur is off.
- Text over the camera preview uses `text` only.
- Icons: stroke 2, 24 dp, drawn for OOS Cam in the Lucide manner (`design/generate_screens.py` has the paths); Lucide (ISC) may be used directly. No emoji, no Apple glyphs.

## States

Empty library, export error with retry, export progress with cancel, confirm delete (destructive), open dropdown, loading skeleton, toast after save, damaged file tile, permission sheet. All are on `11-components-states`.

## Motion

150 ms presses, 250 ms sheets and shutter morph (circle → rounded square while recording), easing cubic-bezier(0.2, 0.8, 0.2, 1). Reduced motion: fades only.

## Not used

Date picker: the MVP has no date input.
