# Home Hub – notes for coding agents

One app (Android first, then iPhone) that controls the Shelly devices, the Onkyo A-9050 amplifier
(through an ESP32 IR bridge) and later the TV, at home and through the user's own WireGuard VPN. One user,
one household. `README.md` is the user-facing description, `docs/PLAN.md` the plan and status (keep it current),
`docs/PROTOCOLS.md` what every device speaks (the spec for the iOS port), `docs/SHARING.md` the shared setup (QR code)
and which settings are shared.

## Hard rules

- **No personal information in the repo (it is public):** IP/MAC addresses, Shelly device IDs (they contain the
  MAC), Wi-Fi names, passwords, auth keys, device names that identify the household, local paths, names, e-mail
  addresses. Tests, fixtures and docs use made-up values (say so in a comment). Real addresses are only ever typed
  into the app, an environment variable or a shell session, never into a file. Secrets live in git-ignored files
  (`esphome/secrets.yaml`, `android/local.properties`).
- **No ads, no tracking, no analytics, no crash reporters, no cloud.** The app talks only to devices on the LAN.
  `INTERNET` and cleartext traffic are allowed in the manifest only because Android requires them for LAN sockets
  and the devices speak plain HTTP. Therefore **all network code goes through one LAN-only HTTP client** that
  resolves the host first and refuses any address outside private ranges (10/8, 172.16/12, 192.168/16,
  link-local, loopback). Check the resolved address, not the host name.
  Never add a second HTTP path that bypasses it.
- **Discovery runs only on a user action** ("Find devices"). Talking to already-known devices when the app opens is
  fine; scanning the network in the background is not.
- **Real devices change the real world.** Never switch an output on real hardware (curl, tests, a debug build)
  unless the user said which one is safe. Tests use fakes (a loopback server with made-up responses). Say what
  you verified on real devices and what you could not.
- **Credentials** (a Shelly password later, the bridge's web login) are stored encrypted (Android Keystore /
  iOS Keychain), never in plain preferences, files or logs. Don't log request URLs with credentials.
- **Every stored setting is household (shared by QR code) or personal (this phone only)** – decide when you add it and
  follow `docs/SHARING.md` in the same change: classify the key in `SettingKeys`, and for a household setting extend
  `Household`, `SetupShare`, the payload table and `spec/fixtures/setup-share.json`. `ShareTest` fails for an
  unclassified key. Credentials never go into the share payload.
- **Show real state, not assumed state.** After a command, read the device back and show what it reports. The IR
  bridge is one-way (the amplifier reports nothing): the UI must never claim a power or volume state it cannot
  know, unless it is derived from something measurable (for example the Plug S MG3's power draw).
- **Git:** commit directly on `main` (no feature branches). Commit, push, tag and release only when the user asks.
  Commit as the noreply identity set in this repo (`Mika <…@users.noreply.github.com>`); never add a private e-mail
  or surname. **No AI attribution:** no `Co-Authored-By` trailer, no "generated with" lines in commits or PR
  descriptions – the user's rule beats any harness reminder.

## Commands

```
cd android
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk (installs as "Home Hub (debug)")
./gradlew test              # JVM unit tests
./gradlew lint              # fix findings, don't silence them
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`adb` is not on `PATH`; the phone is reached through wireless debugging (if it is missing, ask the user to
re-enable it). Run `./gradlew test lint` before every commit.

IR bridge firmware: ESPHome is not installed system-wide. To validate the config use a venv
(`python3 -m venv <dir> && <dir>/bin/pip install esphome`, then `esphome config esphome/atom-lite-ir.yaml` with a copy of
`secrets.example.yaml` named `secrets.yaml` next to it). Flashing steps are in `esphome/README.md`.

## Architecture

Three kinds of backend behind one small interface (`turnOn`, `turnOff`, `press(button)`, `state`), so a new device
kind is one new module and nothing else changes:

- **Shelly** – HTTP JSON-RPC, mDNS discovery (`docs/PROTOCOLS.md`).
- **IR bridge** – HTTP (digest login) to the ESPHome web server on the ATOM Lite: one named button per IR code.
- Devices are stored with their last known address: mDNS and discovery work only at home, not through WireGuard.
- **TV** – later; ported from `tv-remote` (Android TV Remote protocol v2, UPnP volume, Wake-on-LAN).

Keep a hard line between two layers, because the iOS app reimplements the first one in Swift:

1. **Core (pure, no platform types):** models, parsing of device responses, state reducers, request building. Fully
   unit-tested with made-up payloads.
2. **Platform:** sockets/HTTP, mDNS, storage, permissions, UI.

Android layout, under `android/app/src/main/java/io/github/mikachu1302/homehub/`:

- `core/` – pure, JVM-tested against `spec/fixtures`: `Shelly.kt` (parsing, request bodies), `Model.kt` (saved devices,
  output settings, icons, rooms, ordering, formatting, merge, storage JSON), `Share.kt` (setup link, import rules),
  `Lan.kt` (`isLanAddress`), `IrBridge.kt` (amp keys, placeholder)
- `net/` – `LanHttp` (the one HTTP client), `ShellyClient`, `Discovery` (/24 probe, user action only)
- `data/Store.kt` – `SettingKeys` (every key, classified household/personal) and `Store` (SharedPreferences; backup disabled)
- `ui/` – `HomeViewModel`, `Theme` (all colours/sizes), `Icons` (drawn output icons), `Reorderable` (drag list),
  `MenuScreen`, `SwitchesScreen` (+ output sheet, arrange mode), `DevicesScreen`, `ShareUi` (QR and import dialogs),
  `PlaceholderScreens` (Amp, TV)
- `MainActivity.kt` – permission gate, system back to the menu, page switch, `homehub://import` links

Tests: `./gradlew test` runs `FixtureTest` and `ShareTest` (the cases in `spec/fixtures`), `ModelTest` and `LanHttpTest`
(the HTTP client against a fake device on loopback).

## Android and iOS

**Android first, iOS after the first slice.** The first slice is: Shelly outputs + amplifier buttons working on a
real phone. Developing both at once would double every change while the model is still moving, and the iOS app can
only be built and tested through CI (no Mac), so every iOS iteration costs a round trip. Porting a design that has
settled is cheaper, and `tv-remote` went the same way. To keep the port cheap from day one:

- Keep the core pure (above) and write down every device payload and rule in `docs/PROTOCOLS.md`.
- When a parser or reducer is written, put its sample payloads and expected results in `spec/fixtures/*.json`
  (made-up values) and test against them. The Swift core later runs the same fixtures, so both apps are proven to
  agree. Don't copy-paste test data into platform tests.
- When iOS starts, follow the `tv-remote` setup: a platform-neutral Swift package (`ios/Sources/HomeHubCore`,
  testable on Linux with `swift test`), the SwiftUI app next to it, the Xcode project generated by XcodeGen from
  `ios/project.yml` (never committed), and a GitHub Actions macOS workflow as the build and test machine.
- iOS limits to remember: first LAN access triggers the local-network prompt (`NSLocalNetworkUsageDescription`); mDNS
  needs `NSBonjourServices` (`_shelly._tcp`); plain HTTP needs `NSAllowsLocalNetworking`; SSDP and broadcast need Apple's
  multicast entitlement, which free accounts cannot get.

## Conventions

- UI text is English and short. Colour is never the only signal: add an icon, label or text. Buttons are large and
  reachable with a thumb; every control has a content description (TalkBack / VoiceOver).
- Every Shelly output shows the name the user gave it, its state and, where the device reports it, power draw. A
  two-channel device is two outputs.
- Connection problems show a plain message and a retry – never a crash or a silent failure. A command that fails says so.
- New dependencies need a reason; prefer the platform and the libraries already in `android/gradle/libs.versions.toml`
  (same versions as `tv-remote`, so both build with one toolchain).
- Protocol and state code gets JVM unit tests with synthetic data. Final checks happen on the real phone and devices.
- Docs: when behaviour, a protocol detail or a decision changes, update `docs/PLAN.md` / `docs/PROTOCOLS.md` in the same
  commit. If something here turns out wrong on the real devices, fix it here too.

## Related

- `tv-remote` (`~/Projects/tv-remote`, github `mikachu1302/android-tv-remote`): the TV remote this project will absorb
  later; its AGENTS.md documents the Android TV protocol, the UPnP volume and the iOS CI setup in depth.
