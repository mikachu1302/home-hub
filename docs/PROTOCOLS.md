# Protocols

What every device speaks. This is the spec the iOS app is written from, so keep it exact. Every section says what
was **verified on the real device** and what is only taken from documentation. All addresses and IDs below are
made up (`192.0.2.x` is a documentation range, device IDs are invented).

## Shelly (Gen2 / Gen3 devices)

Verified on: Plus 1PM (Gen2), Plus 2PM (Gen2), Plug S MG3 (Gen3), 2026-11-10. No authentication enabled.

### Discovery
- mDNS service `_shelly._tcp` on port 80. TXT records seen: `gen=2` (or `3`), `app=Plus1PM`, `ver=1.0.7`. The instance
  name is the device ID (`shellyplus1pm-<12 hex digits, the MAC>`), the host name `<ID>.local`.
- Fallback when mDNS misses a device (some networks, iOS without entitlements): probe `GET http://<ip>/shelly` on every
  address of the /24. A Shelly answers JSON with `id`, `model`, `gen`; other hosts answer HTML or nothing. Use a short
  timeout (about 2 s) and many parallel probes.

### Identity
`GET /rpc/Shelly.GetDeviceInfo` → `{ "name": null, "id": "shellyplus1pm-…", "model": "SNSW-001P16EU", "gen": 2,
"app": "Plus1PM", "ver": "…", "auth_en": false, … }`. `name` is null until the user names the device in the Shelly app;
our own names for outputs are stored in the app, not on the device.

### Outputs and state
Each relay channel is one output, addressed by `id` (0 for single-channel devices; the 2PM has 0 and 1; the Plug S MG3
has 0).
- `GET /rpc/Switch.GetStatus?id=0` → `{ "id": 0, "output": true, "apower": 7.6, "voltage": 230.4, "temperature":
  { "tC": 38.7 }, … }`. `apower` is the power in watts, `voltage` in volts. Not every device reports every field: the
  parser must treat all but `output` as optional.
- `GET /rpc/Shelly.GetStatus` → everything in one object, with keys `switch:0`, `switch:1`, … (use this to read all
  channels of a device in one request).
- `GET /rpc/Switch.Set?id=0&on=true` (or `false`) → `{ "was_on": false }`. The response is the state *before* the
  change, so read the status back to show the new state.
- `GET /rpc/Switch.Toggle?id=0` → `{ "was_on": false }`, same rule.

Verified: reading all fields above, `Switch.Toggle` and `Switch.Set` on a 1PM, read-back after 2 s showing the new
state and a power draw.

The app sends every call as JSON-RPC `POST /rpc` with `{"id": n, "method": "…", "params": {…}}` and reads `result`, or
`error.message` on failure: `{"id":1,"src":"<device id>","error":{"code":404,"message":"No handler for Switch.Nope"}}`.
Verified with reads on Gen2 and Gen3, and switching/timers through the app (2026-11-10).

### Timers (auto-off / auto-on)
Run on the device itself: they work without the app and without the cloud.
- `GET /rpc/Switch.GetConfig?id=0` → `{ "id": 0, "name": null, "auto_off": true, "auto_off_delay": 420.00, "auto_on": false,
  "auto_on_delay": 60.00, "initial_state": "restore_last", … }`. Delays are in seconds. `initial_state` (after power loss)
  is `restore_last`, `match_input`, `on` or `off`.
- Verified: reading the config on all five devices (2026-11-10). Two outputs use auto-off.
- Changing it: `Switch.SetConfig` with `{"id":0,"config":{"auto_off":true,"auto_off_delay":420}}` → `{"restart_required":false}`.
  Verified 2026-11-10 by the user through the app.

### Schedules
`GET /rpc/Schedule.List` → `{"jobs":[],"rev":0}` on every device (verified: none in use). Schedules would run on the
device (`Schedule.Create`, cron-like `timespec`, sunrise/sunset possible); not needed now.

### Not verified here (from Shelly's documentation)
- Live updates: WebSocket on `/rpc` delivers `NotifyStatus` / `NotifyEvent` frames. Polling every few seconds while
  the screen is visible is the simple fallback.
- Authentication: Gen2/Gen3 use HTTP digest authentication when enabled in the device settings (`auth_en: true`).
- Gen1 devices (`/relay/0?turn=on`) are out of scope; none in the household.

## IR bridge (M5Stack ATOM Lite, ESPHome)

Config and setup: `esphome/`. The bridge's host name is `ir-bridge.local` unless changed in the config (mDNS: at home only, not through the VPN, so the app stores the address).

- ESPHome `web_server` (version 3) on port 80, HTTP **digest** authentication (RFC 2617; `auth: type: digest` in the config).
- The server answers 403 to a request whose `Origin` header is not its own: send no `Origin` header.
- Press a button: `POST /button/<name>/press`, where `<name>` is the button's **name** (not an id), percent-encoded:
  `POST /button/Amp%20Volume%20Up/press` → HTTP 200 with an empty body. Unknown name → 404.
- State of a button: `GET /button/<name>` → JSON; buttons have no state, so this only proves they exist.
- Verified: the URL scheme, name matching, `%20` decoding and the login type (by reading the ESPHome 2026.10 source); the firmware compiles. **Not verified on hardware**
  (not yet delivered): the real round trip, the IR range, the pin assignment of the Unit IR's receiver.
- One-way: the amplifier reports nothing, so the bridge cannot tell the app the volume, input or power state.

### Onkyo A-9050 codes
Onkyo remotes use a NEC-style protocol (ESPHome is expected to decode it as `nec`; it has no Onkyo-specific protocol). The real address and command values
are captured from the user's remote once the hardware is here and are kept in `esphome/atom-lite-ir.yaml`
(they are not secrets, but they are not needed anywhere else).

## Hisense TV (58A6FG, VIDAA)

Documented in `tv-remote` (`AGENTS.md`, "The TV itself"): volume and mute over UPnP RenderingControl (port 38400,
plain HTTP + SOAP, no pairing), power on by Wake-on-LAN. Power off and input switching are not possible over the
network and go through the IR bridge. **Unverified:** that the TV accepts IR commands at all (the voice remote shows
no IR flash with a phone camera, so it is probably Bluetooth only); the TV's IR codes would come from a code database
and are tested once the bridge is here.

## Android TV stick (waipu.tv 4K)

Not part of the first slice. Documented in `tv-remote` (Android TV Remote protocol v2 over TLS with pinning). When it
is ported, the protocol section moves or links here.
