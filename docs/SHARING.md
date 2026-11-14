# Sharing the setup between phones

Two people in the same household use the app. Sharing copies settings from one phone to the other as a QR code (or
the same link sent as text). **Both sides choose:** the sender ticks which parts go into the code (household,
preferences, passwords), and the receiver confirms, with a separate choice for the preferences. Only the app's
own state never travels. This file is the contract; read it before adding any setting.

## The rule for every new setting

**Every setting the app stores belongs to exactly one class. Decide which when you add it.**

| Class | What | Shared? | Examples |
|---|---|---|---|
| **Household** | describes the home | when the sender ticks it (on by default) | device list and addresses, output names, rooms, icons, output and room order, the IR bridge address |
| **Preference** | how a person likes to use the app | when the sender ticks it (on by default); the receiver can still decline | list view ("By room"/"All"), menu order and menu names; later theme, colours, haptics |
| **State** | what the app remembers about itself | never: copying it would only do odd things | the page that was open last, hints already shown |
| **Credential** | passwords, keys | only with "Include passwords" (see below) | the IR bridge login; later a Shelly password |

Then, in the same change:

1. Add the key to `SettingKeys` in `android/app/src/main/java/io/github/mikachu1302/homehub/data/Store.kt`, in
   `HOUSEHOLD`, `PREFERENCES` or `STATE`. `ShareTest.everySettingIsClassifiedExactlyOnce` fails until you do.
   (Credentials are not in `SettingKeys`: they live in the Keystore.)
2. **Household or preference:** add it to `Household` or `Preferences` and to `SetupShare` (`core/Share.kt`), decide
   how it merges on import (below), add it to the payload table here, extend `spec/fixtures/setup-share.json`, and
   adjust the expected sets in `everySettingIsClassifiedExactlyOnce`. A preference must fall back to its default
   when the value is missing or unknown (a link from an older or newer app). If an older app could misread the new
   payload, bump `SetupShare.VERSION`.
3. **State:** nothing else; it must never appear in the payload.

## Credentials ("Include passwords")

The other phone needs the bridge login to send amp commands, and typing it on every phone is tedious. Rules:

- Off by default. The sender switches "Include passwords" on for this one QR code; it is not remembered.
- While it is on, the dialog says plainly that anyone who sees or photographs the code can read the passwords.
- The checkbox only appears when this phone has a password to share.
- The receiver's import dialog lists which passwords come with it; they are stored encrypted (`SecretStore`: an AES
  key in the Android Keystore) and replace the ones on that phone.
- The payload gets an optional `secrets` object; links without it import exactly as before. A link may carry only
  passwords.

Adding a field that older apps simply ignore (a new optional key) does not need a version bump. Removing or changing
the meaning of a field does.

## Choosing what to share

- **Sender** (Settings → "Show QR code"): one checkbox per part – "Devices, names, rooms, icons, order" and
  "My preferences", and "Include passwords" (off each time). The QR code and "Copy link" follow the ticks; with nothing ticked
  there is no code. The choice is per sharing and not remembered.
- **Receiver**: the import dialog lists only the parts the code carries. With household and preferences it offers
  "Also use the other phone's preferences"; with preferences only, importing applies them.
- A new class or part gets its own checkbox on the sender's side; a part is one class, not single settings
  (keeps the dialog short; finer choices only if they are missed).

## Link format

```
homehub://import?d=<data>
```

`<data>` is the JSON payload (below), compressed with raw DEFLATE (no zlib header; Java `Deflater(level, nowrap=true)`,
Python `zlib.compressobj(9, DEFLATED, -15)`, Apple `COMPRESSION_ZLIB`) and encoded as base64url without padding.
The QR code uses error correction level M. Five devices with names, rooms and icons give a link of about 340
characters; a code at level M holds 2331 bytes, so there is plenty of room.

Receiving: the phone's camera opens the link, Android starts Home Hub (`intent-filter` for scheme `homehub`, host
`import`), or the user pastes the link (Settings → "Paste link"). Surrounding spaces and line breaks are ignored.
**Nothing changes before the user confirms** the import dialog. The iPhone camera opens links of registered schemes
the same way, so the iOS app only has to register `homehub`.

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
  "roomOrder": ["Living room"],
  "prefs": {"switchView": "ROOMS", "menuOrder": ["SWITCHES", "AMP", "TV"], "menuNames": {"SWITCHES": "Lights"}}
}
```

| Field | Meaning | On import |
|---|---|---|
| `v` | payload version | newer than the app knows → refused with "Update the app first" |
| `devices[]` | the Shelly devices, same shape as the app's own storage (`DeviceJson`). **The household part is present exactly when `devices` is**; `outputOrder`/`roomOrder` belong to it | matched by `id`: the shared device replaces the local one (address, outputs with name, room, icon); devices only on the receiving phone stay; absent → devices untouched |
| `outputs` | per channel: `name`, `room`, `icon` (an `OutputIcon` id; unknown ids show the generic icon) | part of the device |
| `outputOrder` | output keys `<device id>/<channel>` | shared order first, local-only keys after it |
| `roomOrder` | room names | shared order first, local rooms not in it after (compared ignoring case) |
| `irBridge` | the IR bridge's address (household part; left out when not set up) | replaces this phone's bridge address; absent → kept |
| `prefs` | the sender's preferences; optional (left out by the sender, or a link from the first version) | replace this phone's preferences only if the receiver agrees |

| `prefs.switchView` | `ROOMS` or `ALL` | unknown value → `ROOMS` |
| `prefs.menuOrder` | menu sections as `Section` names (`SWITCHES`, `AMP`, `TV`, `TV_IR`) | unknown ids are ignored; sections not listed come last; missing → default order |
| `prefs.menuNames` | the user's titles, keyed by `Section` name | blank or missing → the default title |
| `secrets` | only with "Include passwords": `{"irBridge": {"user": …, "password": …}}` | stored encrypted, replaces this phone's bridge login; an entry with an empty user is ignored |

A payload with none of the parts (household, `prefs`, `secrets`) is refused ("This setup is empty.").

Not in the payload: everything in `SettingKeys.STATE`, and credentials unless "Include passwords" was on.

## It is a copy, not a sync

Sharing copies the setup once. A later change on one phone needs sharing again. Real sync would need storage both
phones reach (for example a file on the user's Nextcloud) and is out of scope until copying turns out to be annoying.

## The iPhone app

It must read and write exactly this format: the same link, the same payload, the same import rules. Its tests run
`spec/fixtures/setup-share.json`, whose links were made with Python's zlib rather than the Android app, so both apps
are checked against an independent encoder.
