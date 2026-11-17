# Design

Decided 2026-11-17: Home Hub takes over the look of `tv-remote` unchanged, so the TV remote can move in later
without being redesigned. Its rules (tv-remote `AGENTS.md`, "Two styles, one layout") apply here too.

## Rules

- **Two styles, one layout.** *Neon CRT*: square keys, glow, scanlines, text labels, VT323. *Glass*: round keys,
  soft glow, a circular dial, icons, Fira Code. The user picks style, palette and scanlines in Settings → Look (a preference, shared on request: docs/SHARING.md).
- **Everything reads `LocalTerm`** (style + palette) or the Material colour scheme made from it, never a hard-coded
  colour. CRT text is scaled up through the density's font scale (VT323 is drawn small), so sizes are plain `sp`.
- **Palette roles:** `primary` = alive (an output that is on, a successful send, + and −), `action` = the action
  (OK), `soft` = a quiet third colour, `warn` = problems (no connection, a failed or slow send). `PaletteTest`
  enforces contrast (text 7:1, other text 4.5:1, borders 3:1): a new palette must pass it.
- **All palettes are dark.** The phone's light/dark setting no longer changes the app; the system bars always get
  light icons.
- Colour is never the only signal (AGENTS.md): "On"/"Off" is written on the key, a send says ✓ or ✗.
- **Names are capitalised** (decided 2026-11-17): page, device and settings-page names are written in Title Case
  wherever they appear as a name – headings, menu entries, settings rows and in running text: "Android TV", "TV",
  "HDMI Switch", "IR Bridge", "App Shortcuts". Everything else stays sentence case: buttons, dialog titles
  ("Share setup", "Import setup?"), labels and messages. Short forms like "the bridge" stay lower case.

## How the Home Hub pages use it

**One header on every page**, tv-remote's (`PageHeader`): glass is one rounded pane with round keys on either side,
the title and a status chip (coloured dot and text) under it; CRT is a row with square keys, the title as "> NAME" in
the alive colour and an upper-case status line. Every page below the menu starts its header with a small back key (2026-11-17). Menu: Edit and Settings on the right;
Switches: Edit (order); Settings: tv-remote's settings header (back key, large title); IR pages: Power top right, as
tv-remote's PWR key (tried below the header and at the bottom: both felt wrong). In the Pride palette a thin rainbow line runs under the header.

| Page | Built from |
|---|---|
| Menu | one pane per section with its status line (alive colour when something is on, warn when nothing answers); glass adds an icon |
| Switches | one pane per output; an output that is on glows in `primary`, the On/Off key is tv-remote's key with `active`. "Grid" (a preference) shows compact cards two to a row: tap the card to switch (on release), hold it for name, room, icon and timers |
| Amplifier | header with Power; one centered group: tv-remote's VOL rocker with Mute below it, and beside it on the right the input as an upright rocker of the same total height (up = next, down = previous) |
| Android TV | tv-remote's screens as they are (its header with the TV switcher and status chips, without the "tv on/off" chip so the open app fits in the row (the power key still glows while the stick is on), the connection as a link icon (glass; CRT keeps its text line), chips on one line that never wraps, app row, the swipeable pad / number pad / more keys, the bottom cluster, keyboard bar, find and pair: with the page header and its back key, the search starts on "Find TVs" or "Add a TV"), without its settings key. tv-remote's settings are split by what they belong to (2026-11-17): Settings > Remote (haptics for every key; keep-awake and theater mode on every control page; smooth press for the stick), Settings > TV (the TV's network volume and Wake-on-LAN MAC), Settings > Android TV (paired sticks, app shortcuts). The app row's "+" opens the app shortcuts, and back returns to the Android TV page |
| HDMI Switch | its own page and menu entry: inputs 1–5 and Next as large keys |
| TV (IR) | laid out like tv-remote's remote: header; tv-remote's D-pad in the middle (CRT cross / glass dial, for the input menu); at the bottom the VOL rocker, Wake (Wake-on-LAN, moved here from the TV page), Mute and Back as tv-remote's cluster keys, and Source as the wide key in tv-remote's Play/Pause spot; Power in the header |
| Settings | an overview with one row per topic and its summary (Devices, IR Bridge, Remote, TV, Android TV, Look, Share), each opening its own page; tv-remote's back key, cards, style cards and palette chips. Back goes page → overview → menu |
| Menu, Edit | the menu's own "Edit" key: drag entries into order, rename them, "+ New page" (decided 2026-11-17: order is changed on the menu itself, not in Settings) |

## Pride palette (decided 2026-11-17)

The Pride palette shows the flag beyond the dial ring: each menu icon, the page dots and the HDMI keys 1–5 take the
next flag colour (`Palette.accent`); a thin rainbow line runs under the page header (`FlagLine`); the glass background
glows in the flag's colours instead of only pink and light blue. Colours with a meaning stay: "on" is light blue, OK
is pink. Other palettes have no flag and are unchanged.

Menu names (default titles, 2026-11-17): **"Android TV"** is the stick's remote (from tv-remote, `Section.TV`), **"TV"**
is the TV over IR (`Section.TV_IR`, called "TV (IR)" elsewhere in these docs). The user can rename both.

## Custom pages (decided 2026-11-17)

The user builds own pages from **ready-made blocks stacked top to bottom** (not a free grid of single keys): each block
lays itself out in both styles, so a page always looks right. The built-in pages (Amplifier, TV (IR), HDMI Switch, Android TV)
**stay fixed**; custom pages are extra menu entries, ordered with the rest.

- Blocks (`BlockKind`): amplifier volume and mute; amplifier power and input; TV (IR) arrows, OK, back; TV (IR) power,
  source, mute; TV (IR) volume; HDMI Switch inputs; one Shelly output; and the Android TV blocks (2026-11-17), pieces of
  tv-remote's remote with its own keys: app shortcuts, arrows and OK, power/home/back/menu/play, volume and mute (the
  TV's own volume when set up), number pad, playback and more keys. A page with Android TV blocks connects to the stick
  while it is shown; its status line then shows the connection (IR blocks: the last IR key). Each block once per section (a switch block once per output).
- A page has one or more **sections** (decided 2026-11-17). With more than one, everything under the header swipes
  sideways between them, with tv-remote's dots below; a touch on a key waits 100 ms to see whether it is a swipe
  (tv-remote's `LocalSwipeable`), so a swipe never presses a key. No fixed blocks for now.
- A page has the shared header (title, IR send status, "Edit"). Edit mode: name, one tab per section and
  "+ Section", the section's block list (drag, Remove), "+ Add block", "Remove section", "Delete this page". A new
  page opens in edit mode.
- Pages are a preference (shared on request, docs/SHARING.md); a block's id never changes.

## App icon (decided 2026-11-17)

A lowercase **h in CRT pixels** (a 7-unit grid, 6-unit pixels with a gap, like a monitor's dot mask) in the palette's
"alive" colour, with a **cursor block** in its action colour, on the palette's background; a faint outline gives the
CRT glow. In the Pride palette the h is a rainbow, each row in the next flag colour from top to bottom, with the pink
cursor. Chosen from three rounds of candidates (houses, then abstract shapes). As in tv-remote there is one icon per
palette and the launcher icon follows the colours (`ui/LauncherIcon.kt` enables one manifest alias). It switches when
the app goes to the background, never while it is open: disabling the alias the app was started from closes the app
on Samsung's launcher (found 2026-11-17; tv-remote switches after 1.5 s and has the same problem); a white version serves themed (monochrome) icons. After changing a palette run
`python3 tools/gen_launcher_icons.py`; `LauncherResourcesTest` fails until you do. Not taken over: tv-remote's five
Pride flag icons.

## Code

`ui/Theme.kt` (styles, palettes, fonts, glow, pane, background, scanlines, themed Material buttons) and `ui/Glyphs.kt`
are copies of tv-remote's `Theme.kt` and `Icons.kt` (only the package and the theme's name changed, plus a `tv`
glyph); `ui/Controls.kt` has tv-remote's `RemoteButton` without its TV connection, `ui/IrScreens.kt` its D-pad, dial,
rocker and cluster keys. When the TV remote moves in, its screens should need little more than an `onPress` in place of
`vm.press(keyCode)`.

Not taken over: the Pride flag icons. Haptics, smooth press, keep awake and theater mode came with the TV remote
(2026-11-17) and are in Settings > Remote.

Fonts: VT323 and Fira Code, SIL Open Font License (`licenses/`).
