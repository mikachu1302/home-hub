# Sharing the setup between phones

Two people in the same household use the app. They share the **household setup** (which devices exist and how they
are named, grouped and ordered) but each phone keeps its **personal settings**. The setup moves from one phone to the
other as a QR code (or the same link sent as text). This file is the contract; read it before adding any setting.

## The rule for every new setting

**Every setting the app stores is either household or personal. Decide which when you add it.**

| Ask | Household (shared) | Personal (this phone only) |
|---|---|---|
| Would the other person want the same value? | yes: it describes the home | no: it is how *I* like to use the app |
| Examples | device list and addresses, output names, rooms, icons, output and room order; later the IR bridge address, its button list | last page, list view ("By room"/"All"), hints already shown; later theme, haptics |

Then, in the same change:

1. Add the key to `SettingKeys` in `android/app/src/main/java/io/github/mikachu1302/homehub/data/Store.kt`, in
   `HOUSEHOLD` or `PERSONAL`. `ShareTest.everySettingIsClassifiedExactlyOnce` fails until you do.
2. **Household:** add it to `Household` and `SetupShare` (`core/Share.kt`), decide how it merges on import (below),
   add it to the payload table here, extend `spec/fixtures/setup-share.json`, and adjust the expected set in
   `everySettingIsClassifiedExactlyOnce`. If an older app could misread the new payload, bump `SetupShare.VERSION`.
3. **Personal:** nothing else; it must never appear in the payload.
4. **Secrets** (passwords, keys, the bridge login) are neither: they are never in the payload, whatever the class.
   If sharing them is ever wanted, only behind an explicit "include passwords" switch the user turns on each time.

Adding a field that older apps simply ignore (a new optional key) does not need a version bump. Removing or changing
the meaning of a field does.

## Link format

```
homehub://import?d=<data>
```

`<data>` is the JSON payload (below), compressed with raw DEFLATE (no zlib header; Java `Deflater(level, nowrap=true)`,
Python `zlib.compressobj(9, DEFLATED, -15)`, Apple `COMPRESSION_ZLIB`) and encoded as base64url without padding.
The QR code uses error correction level M. Five devices with names, rooms and icons give a link of about 340
characters; a code at level M holds 2331 bytes, so there is plenty of room.

Receiving: the phone's camera opens the link, Android starts Home Hub (`intent-filter` for scheme `homehub`, host
`import`), or the user pastes the link (Devices → "Paste link"). Surrounding spaces and line breaks are ignored.
**Nothing changes before the user confirms** the import dialog.

## Payload (version 1)

```json
{
  "v": 1,
  "devices": [
    {
      "id": "shellyplus2pm-a0b1c2d3e4f5",
      "address": "192.0.2.10",
      "app": "Plus2PM",
      "gen": 2,
      "channels": 2,
      "outputs": {
        "0": {"name": "Lamp", "room": "Living room", "icon": "lamp"},
        "1": {"name": "Strip", "room": "Living room", "icon": "strip"}
      }
    }
  ],
  "outputOrder": ["shellyplus2pm-a0b1c2d3e4f5/1", "shellyplus2pm-a0b1c2d3e4f5/0"],
  "roomOrder": ["Living room"]
}
```

| Field | Meaning | On import |
|---|---|---|
| `v` | payload version | newer than the app knows → refused with "Update the app first" |
| `devices[]` | the Shelly devices, same shape as the app's own storage (`DeviceJson`) | matched by `id`: the shared device replaces the local one (address, outputs with name, room, icon); devices only on the receiving phone stay |
| `outputs` | per channel: `name`, `room`, `icon` (an `OutputIcon` id; unknown ids show the generic icon) | part of the device |
| `outputOrder` | output keys `<device id>/<channel>` | shared order first, local-only keys after it |
| `roomOrder` | room names | shared order first, local rooms not in it after (compared ignoring case) |

Not in the payload: everything in `SettingKeys.PERSONAL`, and any credential.

## It is a copy, not a sync

Sharing copies the setup once. A later change on one phone needs sharing again. Real sync would need storage both
phones reach (for example a file on the user's Nextcloud) and is out of scope until copying turns out to be annoying.

## The iPhone app

It must read and write exactly this format: the same link, the same payload, the same import rules. Its tests run
`spec/fixtures/setup-share.json`, whose links were made with Python's zlib rather than the Android app, so both apps
are checked against an independent encoder.
