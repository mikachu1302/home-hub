# Home Hub

One local app (Android first, iPhone later) for the things in the living room that each have their
own remote or app: Shelly switches and sockets, an Onkyo A-9050 amplifier (through an IR bridge), an HDMI switch and
the TV. Everything stays on your home network; away from home it works through your own VPN.

**Status: Android app in daily use, iPhone app next.** The Android app finds the Shelly devices, switches them, shows
power, edits their auto-off/auto-on timers, groups them by room with icons and your own order, and shares that setup with
another phone by QR code ([docs/SHARING.md](docs/SHARING.md)). It drives the amplifier, the TV and the HDMI switch over
IR, includes the TV remote from [tv-remote](https://github.com/mikachu1302/android-tv-remote) (Android TV stick, TV
volume, Wake-on-LAN), and lets you build your own pages from blocks. See [docs/PLAN.md](docs/PLAN.md).

## Goals

- One screen for Shelly outputs (switch, power draw), the amplifier (power, volume, mute, input) and the TV.
- No cloud, no accounts, no ads, no tracking. The app talks only to devices on your LAN.
- Small and tidy: the smallest version that is useful first, then grow it.

## What controls what

| Device | How the app reaches it |
|---|---|
| Shelly Plus / Plug (Gen2, Gen3) | plain HTTP JSON-RPC on the LAN, found with mDNS |
| Onkyo A-9050 (IR only) | HTTP to a small ESP32 IR bridge (M5Stack ATOM Lite + Unit IR running ESPHome), which sends the infrared codes |
| Android TV stick (waipu.tv 4K) | Android TV Remote protocol v2 over TLS, paired once (from tv-remote) |
| Hisense TV | volume over UPnP and power on by Wake-on-LAN (from tv-remote); power, inputs, menu and volume also over the IR bridge |
| UGREEN HDMI switch | the IR bridge |

Details of every protocol: [docs/PROTOCOLS.md](docs/PROTOCOLS.md).

## Repository layout

```
android/    Android app (Kotlin, Jetpack Compose)
ios/        iPhone app (SwiftUI), starts after the Android first slice, see AGENTS.md
esphome/    firmware config + setup guide for the IR bridge
scripts/    small helpers for testing the bridge from a shell
tools/      gen_launcher_icons.py (launcher icons and splash colours per palette)
spec/       fixtures/: shared test data for the Android and the later iOS core
docs/       PLAN.md (status and plan), PROTOCOLS.md (what we talk to), SHARING.md (the shared setup),
            DESIGN.md (the look)
licenses/   font licences
AGENTS.md   rules and conventions for contributors and coding agents
```

(`ios/` does not exist yet.)

## Build (Android)

```sh
cd android
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk, installs as "Home Hub (debug)"
./gradlew test lint
```

Needs the Android SDK (platform 37) and a JDK. `local.properties` with `sdk.dir=…` is created per machine
and is not committed.

## IR Bridge

Parts: an M5Stack ATOM Lite and a Unit IR (both plug together, no soldering). Setup and firmware:
[esphome/README.md](esphome/README.md).

## Look

The look of the TV remote app (Neon CRT or Glass, ten colour palettes), chosen in Settings. Fonts: VT323 and
Fira Code (SIL Open Font License, see `licenses/`). App logos on the Android TV page: shapes from Simple Icons (CC0,
simpleicons.org); the brands are trademarks of their owners and the shapes only say which app a tile opens.

## Privacy

This repository is public. It contains no real addresses, device IDs, Wi-Fi names or passwords, and tests use
made-up values. See the hard rules in [AGENTS.md](AGENTS.md).
