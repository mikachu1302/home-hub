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
- Digest details (ESPHome 2026.10 source, `web_server_idf.cpp`): challenge `Digest realm="Login Required", qop="auth",
  nonce=…, opaque=…`, MD5. The bridge does not remember its nonces, so a client may reuse one challenge for every
  request (counting `nc` up) and only fetch a new one on a 401; the app does that, so a key press is one request.
  Parsing and responses: `core/Digest.kt`, cases in `spec/fixtures/digest-auth.json`.
- A `POST` needs a `Content-Length` header (an empty body is fine, `curl -d ''`); without one the bridge answers 411.
- Verified on the real ATOM Lite (ESPHome 2026.10.2, 2026-11-11): the URL scheme, digest login (401 without, 200 with),
  presses arriving as IR. Not yet verified: the Unit IR's receiver pin.
- One-way: the amplifier reports nothing, so the bridge cannot tell the app the volume, input or power state.

### Onkyo A-9050 codes
Onkyo codes are NEC with address bytes `D2 <device id>`: `D2` is Onkyo, the second byte selects the device. The classic
Onkyo amplifier id `6D` (most databases, including Flipper-IRDB's "Onkyo / Amplifier") **does nothing on the A-9050**.
A sweep over all 256 ids with Volume Down found id `03`, which matches Flipper-IRDB's `_Converted_/IR_Plus/O/ONKYO/A-Serie.ir`
(Onkyo's A-series integrated amps; it uses ids `03` and `04`). Confirmed on the A-9050, 2026-11-11:

| Key | Id / key | ESPHome `transmit_nec` |
|---|---|---|
| Volume + | 03 / 0x02 | address `0x03D2`, command `0xFD02` |
| Volume − | 03 / 0x03 | `0x03D2`, `0xFC03` |
| Mute (toggle) | 03 / 0x05 | `0x03D2`, `0xFA05` |
| Power (toggle) | 04 / 0xCB | `0x04D2`, `0x34CB` |
| Input next | 04 / 0x1E **+ repeat codes** | raw, see below |
| Input previous | 04 / 0x1F **+ repeat codes** | raw, see below |

The input keys were captured from the original remote with the Unit IR (2026-11-14). The remote sends them like a short
hold: the NEC frame, then NEC repeat codes (9 ms mark, 2.25 ms space, 560 µs mark) 108 ms apart. The A-9050 ignores a
bare input frame (that is why the database codes seemed not to work), so the config sends them with `transmit_raw`:
frame, then two repeat codes. Volume, mute and power need no repeats. The remote has no key per input, only
next/previous.

Tried and **not working**: power 03/0x04 and "OFF" 04/0x47 (so no discrete on/off is known), and the database's
per-input codes (03/0x1B CD, 03/0x15 tape, 03/0x46 FM).

ESPHome sends NEC low byte first: address `D2 03` is `0x03D2`, and the command word is the key plus its inverse in the high
byte, `(~k << 8) | k`. The codes live in `esphome/atom-lite-ir.yaml`.

**Range:** the ATOM Lite's built-in LED and the Unit IR's LED (Grove yellow wire, GPIO26; receiver on white, GPIO32)
both reach only a short distance. Raising the pin drive from 20 to 40 mA made no real difference (tested, reverted).
So the firmware sends everything through the Unit IR, which sits right at the amp's IR window on a Grove cable.

## Hisense TV (58A6FG, VIDAA)

Documented in `tv-remote` (`AGENTS.md`, "The TV itself"): volume and mute over UPnP RenderingControl (port 38400,
plain HTTP + SOAP, no pairing), power on by Wake-on-LAN. Power off and input switching are not possible over the
network and go through the IR bridge. **Unverified:** that the TV accepts IR commands at all (the voice remote shows
no IR flash with a phone camera, so it is probably Bluetooth only); the TV's IR codes would come from a code database
and are tested once the bridge is here.

## Android TV stick (waipu.tv 4K)

Not part of the first slice. Documented in `tv-remote` (Android TV Remote protocol v2 over TLS with pinning). When it
is ported, the protocol section moves or links here.
