# OOS Cam Design System (MASTER)

`tokens.css` is the only source of colors, type, spacing, radii and sizes. `tokens.json` mirrors it for Kotlin code generation. No raw color or size outside these files.

## Origin

- Style: **Liquid Glass**, matched in the ui-ux-pro-max style database (`liquid-glass`): translucent material for navigation and controls only, content on its own layer, reduced-transparency and reduced-motion fallbacks.
- Product palette: the database has **no camera or video product match** (searched "camera photo video", 0 results). A first `--design-system` run classified the app as "Personal Finance Tracker" and was discarded. The palette below was set by hand from the camera context and checked for contrast.
- Feel: taken from the structure of recent iOS Camera and Photos apps (floating glass controls, large round shutter, pill controls, edge-to-edge media). No Apple icons, SF Symbols, fonts, wording or assets. Icons come from Lucide (ISC license).

## Theme

Dark only in the MVP. Camera screens sit on `--color-bg-camera` (#000); Library, Editor and Settings sit on `--color-bg` (#0E0F12).

## Color rules

| Role | Token | Contrast checked |
| --- | --- | --- |
| Main text | `--color-text` #F5F6F8 | 17.7:1 on bg |
| Secondary text | `--color-text-secondary` #A9AEB8 | 6.9:1 on surface-raised |
| Tertiary text | `--color-text-tertiary` #8A909B | 4.8:1 on surface-raised (darkened from #7C828E, which gave 4.1:1) |
| Main action | `--color-accent` #FFC53D with `--color-text-on-accent` | 11.6:1 |
| Destructive button | `--color-danger` #C9342D with white | 5.2:1 (darkened from #D93A32, which gave 4.3:1) |
| Destructive text | `--color-danger-text` #FF7A70 | 6.1:1 on surface-raised |
| Record dot, shutter ring | `--color-record` #FF5A4F | 6.8:1 on black (non-text, 3:1 needed) |

- One accent-filled control per screen.
- Text on glass that floats over the live camera preview uses `--color-text` only. Secondary text failed there (3.8:1 over a bright scene).
- Glass = `--color-glass` + `backdrop-filter: blur(var(--glass-blur))` + 1px `--color-glass-border`. When blur is unavailable (Android < 12 is out of scope, but battery saver and "Reduce glass effects" apply) use `--color-glass-solid`.

## Typography

Inter 400/500/600, bundled. Scale: display 28/34, title 20/26, body 16/22, label 14/20, caption 12/16, timer 17/22 with tabular numbers. Nothing below 12 px.

## Spacing, radii, sizes

- Spacing 4, 8, 12, 16, 20, 24, 32, 40. Screen side padding 16.
- Radii: 10 (chips, inputs), 16 (cards, sheets inner), 24 (sheets, glass panels), pill.
- Buttons: **44 and 56 px high only**. Touch targets ≥ 44×44. Shutter 76 px is a dedicated control, not a button size.
- Top bar 56, tab bar / bottom control bar 64, list row 56, library thumbnails with 2 px gaps.

## Components

Button (md, lg; filled-accent, glass, plain, danger), icon button (44), pill segmented control, custom dropdown, custom checkbox, custom search field, switch, list row, sheet, toast, progress bar, skeleton (keeps thumbnail and row sizes), badge, clip chip, timeline, trim handle. No native `<select>`, `input[type=date|checkbox|search]` in mock-ups; Compose equivalents are custom.

Date picker: not used. The MVP has no date input, so no date picker is built.

## Motion

150 ms for presses, 250 ms for sheets and glass morphs, easing cubic-bezier(0.2, 0.8, 0.2, 1). Reduced motion: fades only.

## Anti-patterns

- Glass on content (thumbnails, video) instead of on controls.
- More than one accent control per screen.
- Secondary or tertiary text over the camera preview.
- Emoji as icons; Apple-looking icons or glyphs.
