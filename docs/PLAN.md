# Home Hub – plan

One app to control the TV, the Shelly devices and the Onkyo amplifier, at home and
(through WireGuard) away from home.

Status as of 2026-11-10. Keep this file current; it is the single source of truth.

## Goal and scope

- Control, from one screen: Shelly switches/socket, Onkyo A-9050 (power, volume, mute, input) and, later, the TV (Hisense TV + Android TV stick, ported from `tv-remote`).
- Everything local. No dependence on a vendor cloud for control.
- Smallest useful version first. Everything marked *later* stays out until it is actually wanted.

## What we know (verified 2026-11-10)

### Shelly
- 5 physical devices = **6 controllable outputs** (the Shelly app counts channels):
  - 3x Shelly Plus 1PM (Gen2, 1 channel each)
  - 1x Shelly Plus 2PM (Gen2, 2 channels)
  - 1x Shelly Plug S MG3 (Gen3, the socket, reports power)
- Found via mDNS (`_shelly._tcp`) and by probing `/shelly` on the LAN.
- **No authentication enabled** on any of them. Fine behind the router; enable auth before exposing them any other way.
- Local control confirmed end to end by toggling one relay and reading back its state.
- API (Gen2/Gen3, JSON-RPC over HTTP), `<ip>` is the device address:
  - Status: `GET http://<ip>/rpc/Switch.GetStatus?id=0` -> `output`, `apower`, `voltage`
  - Set: `GET http://<ip>/rpc/Switch.Set?id=0&on=true`
  - Toggle: `GET http://<ip>/rpc/Switch.Toggle?id=0`
  - Device info: `GET http://<ip>/rpc/Shelly.GetDeviceInfo`
  - The 2PM uses `id=0` and `id=1` for its two channels.

### Onkyo A-9050
- Integrated amplifier, **no network control**. Controllable by IR (remote), maybe also the wired RI port (not investigated, not needed for now).
- IR is one-way: we can send commands but cannot read volume/input/power state back.
- Onkyo codes are NEC-style. Exact address/command values still to be captured from the real remote.

### Hisense TV (VIDAA) – from the `tv-remote` project notes (AGENTS.md)
- Already handled over the network by `tv-remote`: volume/mute with level readback (UPnP) and power on (Wake-on-LAN).
- **Not possible over the network:** power off and input switching. VIDAA's own remote protocol needs a Hisense-issued client certificate, and a phone as Bluetooth remote was tried and dropped. The notes conclude these need a Wi-Fi IR bridge, which is what the ATOM Lite is.
- So the IR bridge also carries the TV's *power off*, *input* (and any other key) buttons. Volume and power on stay with UPnP / Wake-on-LAN.
- **Remote (model 58A6FG, voice remote):** phone-camera test on 2026-11-10: the **Onkyo remote shows an IR flash** (so the camera can see IR and the amp is capturable), the **Hisense remote shows nothing**, so it is most likely Bluetooth/RF only. The remote's `ERF…` code (inside the battery compartment) is not yet known.
- **Unverified:** whether the TV accepts IR at all. A Bluetooth-only remote says nothing about the TV's own IR receiver, which very likely exists. Since there is nothing to capture, the TV codes must come from a code database (Hisense). Test with the bridge once it arrives: send the power code and see if the TV reacts. If it does not, the TV stays on Wake-on-LAN plus the Hisense app for power off.
- One bridge may cover both TV and amp if they are side by side and the LED beam reaches both; otherwise a second bridge or an external LED.

### Remote access
- WireGuard on the FRITZ!Box is already set up, so the app just uses the same LAN addresses when away.

## Architecture

```
            Phone / app
                 |
     LAN (or WireGuard when away)
   +-------------+---------------+
   |             |               |
 Shelly x5    IR bridge       Android TV
 (HTTP RPC)   (ESPHome,       (existing
              HTTP)           tv-remote
                 |            protocol)
              IR LED
                 |
              Onkyo A-9050
```

- **IR bridge** = M5Stack ATOM Lite (ESP32) running ESPHome. Its built-in LED sends the Onkyo codes. An M5Stack Unit IR on the Grove port (its receiver, learning only) lets us capture codes from the original remote.
- App talks to every backend through one small interface per device kind (`turnOn`, `turnOff`, `press(button)`), so a new device type is one new module and nothing else changes.

## Hardware

| Item | Status | Notes |
|---|---|---|
| M5Stack ATOM Lite | **ordered 2026-11-10** | built-in IR LED (GPIO12, confirmed in M5Stack's docs), weak range: place it close to and aimed at the amp |
| M5Stack **Unit IR (U002)** (plugs into the Grove port, no soldering) | **ordered 2026-11-10** | receiver to learn codes, plus a second IR emitter (unused for now). A bare TSOP38238 would also work but needs wiring: power it from 3V3, never 5V |
| USB-C cable + USB charger | have? | permanent power for the bridge |
| External IR LED + transistor | *later, only if range is bad* | |

## Phases

### Phase 0 – discovery (done)
- [x] Find all Shelly devices on the LAN
- [x] Confirm local control without cloud (toggle + read back)
- [x] Choose the IR approach (ESPHome on ATOM Lite, HTTP)
- [x] Prepare ESPHome config and docs (this repo)

### Phase 1 – IR bridge (waiting for hardware)
- [ ] Plug the Unit IR into the Grove port; find the receiver pin by trying (GPIO32 first, else GPIO26)
- [ ] Install ESPHome, copy `secrets.example.yaml` to `secrets.yaml`, flash over USB
- [ ] Reserve fixed IPs in the FRITZ!Box for the bridge and all five Shellys (`*.local` names do not work through WireGuard)
- [ ] Capture every needed button from the original remote (watch the log)
- [ ] Also capture the Hisense remote (power off, inputs); if it prints nothing, it is probably Bluetooth/RF, so use a code database
- [ ] Fill the real codes into `atom-lite-ir.yaml` (amp and TV buttons), re-flash (over Wi-Fi now)
- [ ] Test each button with `scripts/press.sh`
- [ ] Decide on power handling (see "Open questions")

### Phase 2 – the app (Android first)
- [x] Decide: new separate app, port `tv-remote` features later (decided 2026-11-10)
- [x] Platform: Android (Kotlin, Compose) first; iPhone after the first slice, see "iOS" below
- [x] Project setup: Android skeleton that builds and passes lint, README, AGENTS.md, CLAUDE.md, PROTOCOLS.md
- [ ] LAN-only HTTP client (refuses non-private addresses) with tests against a loopback fake
- [ ] Shelly client: discovery via mDNS (+ /24 probe fallback), list devices+channels, switch, show power
- [ ] Device list storage (names for the 6 outputs, which device is which)
- [ ] IR client: buttons for the amp via HTTP to the bridge (after the bridge works)
- [ ] One screen: Shelly switches + amp (TV joins when ported)
- [ ] Test through WireGuard from outside
- [ ] First slice done = Shelly outputs and amp buttons work on the real phone

### iOS (after the first slice)
Decision 2026-11-10: **Android first, iPhone after the first slice**, not both at once. Reasons: the model is still
moving, so two codebases would double every change; iOS can only be built and tested through GitHub Actions (no Mac),
so each iOS iteration is a CI round trip; porting a settled design is cheaper (`tv-remote` went the same way). To keep
the port cheap: a pure core layer, everything written down in `docs/PROTOCOLS.md`, and shared test fixtures in
`spec/fixtures/` that both platforms run. Setup then follows `tv-remote` (Swift package core, XcodeGen, macOS CI).

- [ ] Start iOS once the first slice works on Android

### Later (only when wanted)
- Enable authentication on the Shelly devices and send credentials from the app
- Detect whether the amp is on, by plugging it into the Plug S MG3 and reading its power draw
- More IR devices (soundbar, fan, ...): just more buttons on the same bridge; one bridge per room
- RF 433 MHz devices: separate RF module or an ESP32 variant with RF
- Redesign/theming of the UI

## Open questions

1. ~~Where does the app live?~~ **Decided 2026-11-10: a new, separate app.** The `tv-remote` features get ported into it later; until then `tv-remote` stays as is.
2. **Amp power state.** If the remote has a single power-toggle button, "on" and "off" are the same code and the app can fall out of sync. Options: separate on/off codes if the remote has them, or the Plug S MG3 power reading above.
3. **Which inputs does the remote have?** Decide the input buttons after seeing the remote.
4. **Names of the six Shelly outputs.** Needed before the UI.

## Risks and notes

- **IR range.** The ATOM Lite LED is a single weak LED. Keep it within about 1-2 m of the amp's IR window and pointed at it.
- **IR is one-way.** No state feedback from the amp.
- **Plain HTTP.** The bridge and Shelly traffic is unencrypted. Acceptable on the LAN and inside WireGuard. Do not expose either to the internet directly.
- **Fixed addresses.** Use DHCP reservations in the FRITZ!Box (or mDNS names) so the app does not break when addresses change.
- **ESPHome URL scheme and login.** In ESPHome 2026.10 the web server addresses entities by *name* (`/button/Amp%20Volume%20Up/press`). Renaming a button changes its URL. If ESPHome is upgraded much later, re-check this. The web login uses HTTP digest authentication (set explicitly), so the app needs a small digest implementation; Shelly authentication is digest too.
- **Receiver pin.** IR LED GPIO12 and Grove GPIO26/GPIO32 are confirmed in M5Stack's docs; which of the two Grove pins carries the Unit IR's receiver is not, so it is found by trying. The Unit IR's signal voltage is also undocumented (expected fine, since M5Stack builds Units for these ports).
- **mDNS does not cross the VPN.** Away from home, `*.local` names and discovery do not work. The app stores each device's address (found at home) and uses that; give every Shelly and the bridge a fixed address in the FRITZ!Box. Discovery is a home-only action.
- **Firmware is compiled, not yet run.** The ESPHome config validates and compiles; nothing is verified on the real ATOM Lite until it arrives.
- **No private data in this repo.** No real IP addresses, Wi-Fi names, passwords or device IDs. Secrets live in `esphome/secrets.yaml` (git-ignored). `scripts/press.sh` reads credentials from environment variables.
