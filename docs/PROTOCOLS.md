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

## IR Bridge (M5Stack ATOM Lite, ESPHome)

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
network and go through the IR bridge.

Its remote is Bluetooth only (nothing to capture), but the TV itself **accepts IR**: verified 2026-11-14 with codes from
Flipper-IRDB's VIDAA sets (`TVs/Hisense/Hisense_32A4HAU.ir` and `Hisense_55E7KQ.ir`, identical): NEC extended, address
`00 BF` (ESPHome `0xBF00`), key `k` sent as `(~k << 8) | k`. Hisense's Google TV models use other codes (NEC `04 00`).

| Key | k | Verified |
|---|---|---|
| Power (toggle) | 0x0D | yes, switches off |
| Source (input menu) | 0x12 | yes |
| Up / Down / Left / Right | 0x16 / 0x17 / 0x19 / 0x18 | yes (menu navigation) |
| OK | 0x15 | yes |
| Back | 0x48 | yes |
| Mute | 0x0E | yes |
| Volume − / + | 0x43 / 0x44 | yes, both (+ confirmed by the user 2026-11-17). On the bridge as "TV Volume Down" / "TV Volume Up" since 2026-11-17 (the TV (IR) page); the UPnP volume from tv-remote is ported too |

## UGREEN HDMI switch (5 inputs)

IR only. Codes captured from its remote with the Unit IR (2026-11-17): NEC, address bytes `80 7F` (ESPHome `0x7F80`).
The remote sends every key as the frame plus one or two NEC repeat codes, so the bridge sends frame + one repeat
(`transmit_raw`, like the amp's input keys). Not yet tested from the bridge.

| Key | k |
|---|---|
| 1 / 2 / 3 / 4 / 5 | 0x05 / 0x09 / 0x07 / 0x1B / 0x08 |
| Next | 0x1F |

## Android TV stick (waipu.tv 4K) and the TV's network volume

Ported from `tv-remote` on 2026-11-17 (its code in `android/.../tv/`, unchanged apart from the package, Home Hub's
theme and the LAN check below). These are tv-remote's own notes, taken over as they were; file names refer to the
`tv` package.

The same protocol Google's own remote uses. Reference: `tronikos/androidtvremote2`
(`polo.proto` for pairing, `remotemessage.proto` for control). The stick advertises
`_androidtvremote2._tcp` over mDNS.

- **Pairing, port 6467, TLS** with our client certificate. Messages are length-prefixed
  (varint) protobuf `OuterMessage`, `protocol_version = 2`, `status = 200`:
  pairing request → ack → options → options → configuration → ack, then the TV shows a
  6-digit hex code. Secret = SHA-256 over client modulus, client exponent, server
  modulus, server exponent (unsigned big-endian, no leading zero byte) and the code
  *without its first byte*; the first code byte must equal the first hash byte (typo
  check). Send the hash as `Secret`; `SecretAck` means paired.
- **Control, port 6466, TLS**, same framing, `RemoteMessage`. The TV sends configure → we
  answer with the features we offer; set_active → we answer; ping request every ~5 s → we
  must answer with the same value or the TV drops us. Keys are `remote_key_inject`
  (Android key code; direction `SHORT = 3`, or `START_LONG = 1` / `END_LONG = 2` for a
  held key – "smooth press").
- **What the TV tells us** (`TvState`, applied by the pure function `TvState.apply`):
  `remote_start` (field 40) = power state, `remote_set_volume_level` (50) = volume,
  `remote_ime_key_inject` (20) = current app package and the focused text field,
  `remote_ime_show_request` (22) = a text field got focus (we open the keyboard bar),
  `remote_voice_begin` (30) = voice session id after we send the Search key.
- **What we send besides keys:** text = `remote_ime_batch_edit` (21) with the counters from
  the last ime message (backspace and Enter are plain DEL/ENTER keys); app launch =
  `remote_app_link_launch_request` (90) with a deep link, or `market://launch?id=<package>`
  for any installed app; voice = after Search key and the TV's voice_begin, echo it, stream
  16-bit 8 kHz mono PCM in chunks of at least 8 KB (`voice_payload` 31), end with
  `voice_end` (32).
- **Observed on the real waipu.tv 4K stick:** supports all features (code 639). It reports
  power and the current app, **but not its volume**: the volume message has no maximum or
  level and nothing arrives when volume keys are pressed (volume lives on the TV over
  HDMI-CEC). So the app shows a level or mute state only when a device reports a maximum.
  Remote ATV shows "VOL 0" for the same reason.
- **The TV itself (Hisense VIDAA, found by probing the real one):** the stick's volume keys only reach the TV over
  HDMI-CEC, which is unreliable and reports nothing back. VIDAA TVs run a UPnP/DLNA media renderer (port 38400,
  SSDP, description at `/MediaServer/rendererdevicedesc.xml`): its standard `RenderingControl` service answers
  `GetVolume`/`GetMute` and accepts `SetVolume`/`SetMute` (0-100) over plain HTTP + SOAP, no pairing (`TvAudio.kt`:
  `AudioClient`, `Upnp`; SSDP can miss the TV, so `discoverAudioTvs` also probes the phone's /24 for the port).
  Power on = Wake-on-LAN magic packet to the MAC the user types in (`Wol`; the TV's Wi-Fi/network MAC, from the
  neighbour table or the TV's network info); the TV must have wake-on-LAN/Wi-Fi enabled. **It only wakes the TV from real
  standby** (verified with a laptop: the TV was silent on the network, the packet brought it up in about 6 s). Right after the
  user powers off with the original remote the TV is in quick-start standby (screen dark, UPnP and port 36669 still answer)
  and ignores the packet; a few minutes later it drops into real standby. VIDAA's
  own remote protocol (MQTT over TLS on port 36669) needs a client certificate issued by Hisense's "RemoteCA":
  without it the TLS handshake fails, so keys, power-off and input switching are NOT available that way, and
  Hisense's key must not be copied into this repo. **Tried and dropped: the phone as a Bluetooth HID remote** (LE HID over
  GATT and classic `BluetoothHidDevice`, both worked on the phone and were visible to a laptop scan), but the TV's Bluetooth
  device list never showed the phone (it listed the waipu stick, an audio adapter and its own remote): it filters on something
  an Android app cannot set (device class / GAP appearance). Power-off and input switching therefore need a Wi-Fi IR bridge
  (Broadlink-style) or the Hisense app; Home Hub's IR Bridge (the ATOM Lite) does exactly that.
- **Connection life:** the app connects while a page with the remote is visible (in Home Hub: the Android TV page or a
  custom page with its blocks, never Settings) and closes the connection otherwise; the loop retries with backoff
  (0.5 s … 8 s), re-finds the TV by its mDNS name when its address changed (AGENTS.md: the one discovery exception), and stops with a message if the pinned certificate no
  longer matches.
- **Identity:** an RSA key and self-signed certificate generated in the Android
  keystore (`Identity` in `Tls.kt`); the TV accepts any client certificate and
  remembers the one it paired with. The key never leaves the device.
- If something here turns out wrong on the real stick, fix this section too.

**In Home Hub:** the stick sees the client as "Home Hub" (pairing service name, configure vendor and package;
2026-11-17), tv-remote as "TV Remote". The Keystore key alias and certificate subject stay tv-remote's, so the
pairing made with them stays valid. Every socket of the TV code (TLS to the stick, UPnP HTTP to the TV, the DIAL request, the /24 probe)
resolves its host with `LanHttp.resolveLan` first and refuses anything outside the LAN, like the HTTP client. Wake-on-LAN
sends only to LAN addresses and the limited broadcast 255.255.255.255; SSDP uses the multicast group 239.255.255.250.
Neither leaves the local network. Volume over IR (the bridge's "TV Volume Up/Down", see the Hisense section) is a
second way to the same TV; tv-remote's UPnP volume stays on the Android TV page.

