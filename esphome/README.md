# IR bridge (ATOM Lite + Unit IR)

Firmware and setup for the IR bridge that sends the Onkyo A-9050 (and later TV) codes. Overview of the whole project: [../README.md](../README.md); plan: [../docs/PLAN.md](../docs/PLAN.md).

## 1. Plug in the receiver (learning only, no soldering)

The ATOM Lite has an IR LED to send but no receiver, so we add the M5Stack **Unit IR (U002)**:
plug its Grove cable into the ATOM Lite's Grove port. The Unit has an emitter and a receiver; the
receiver is what captures codes from the original remotes.

M5Stack's ATOM Lite page confirms the Grove port uses **GPIO26 and GPIO32** and the built-in IR LED
is on **GPIO12**. It does not say which of the two carries the Unit's receiver, so the config starts with the receiver
on **GPIO32**. If no codes show up in the log when you press a remote button, change `remote_receiver`
to **GPIO26** and re-flash. (Unit IR docs do not state the signal voltage; M5Stack designs Units for
its own Grove ports, so this is expected to work, but it is not documented.)

(A bare TSOP38238 or a KY-022 module also works but needs wiring; see ../docs/PLAN.md.)

## 2. Install ESPHome (Fedora)

```sh
python3 -m venv ~/.venvs/esphome
~/.venvs/esphome/bin/pip install esphome
# serial port access for flashing; log out and in again afterwards
sudo usermod -aG dialout "$USER"
```

## 3. Flash (run in this `esphome/` folder)

```sh
cp secrets.example.yaml secrets.yaml   # then edit: Wi-Fi, passwords
~/.venvs/esphome/bin/esphome run atom-lite-ir.yaml   # pick the USB serial port
```

After the first flash, updates and logs go over Wi-Fi (`esphome run` lists the device).

## 4. Capture the Onkyo codes

With `esphome logs atom-lite-ir.yaml` running, point the original remote at the Unit IR's
receiver and press every button you want. Lines like `Received NEC: address=0x..., command=0x...`
are expected. Put the values into the matching buttons in `atom-lite-ir.yaml`, replacing the `TODO`
entries, and re-flash. If the log shows a different protocol name, use the matching
`remote_transmitter.transmit_*` action for those buttons (ESPHome has no Onkyo-specific one).

## 5. Test

```sh
export IR_HOST=ir-bridge.local IR_USER=<web_username> IR_PASS=<web_password>
../scripts/press.sh "Amp Volume Up"
```

The bridge's web page at `http://ir-bridge.local/` also has a button for each entry.

## Safety notes

- Never commit `esphome/secrets.yaml`, and never put real addresses or credentials in the docs.
- The bridge speaks plain HTTP; use it on the LAN or through WireGuard only.
