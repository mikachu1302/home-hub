# Shared test fixtures

Sample device responses and the result every platform must produce from them. The Android unit tests and, later,
the Swift core tests read these same files, so both apps are proven to agree. Don't copy the data into platform tests.

**All values are made up** (device IDs, MAC addresses, firmware strings). The shapes follow real responses observed on
Shelly Plus 1PM / Plus 2PM (Gen2) and Plug S MG3 (Gen3) devices.

Format: one JSON object per file, `{"description": "...", "cases": [...]}`. Each case has a `name`, an `input` and
an `expected` result. An `input` that is a JSON string is the raw response text as received (used for non-JSON or
broken answers); any other `input` is a JSON document, passed to the parser as its text.

| File | Parser |
|---|---|
| `shelly-identity.json` | `GET /shelly` → device identity, or `null` when it is not a Gen2+ Shelly |
| `shelly-rpc.json` | JSON-RPC envelope → `result`, or the error message |
| `shelly-switch-status.json` | `Shelly.GetStatus` result → the switch outputs |
| `shelly-switch-config.json` | `Switch.GetConfig` result → the timer settings |
| `delay-format.json` | seconds → the label the UI shows |
| `room-grouping.json` | outputs with a room → room headings and their outputs |
| `lan-address.json` | IP address literal → whether the LAN-only client may talk to it |
