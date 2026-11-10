#!/usr/bin/env bash
# Press a button on the IR bridge by its name, e.g.:
#   IR_HOST=<ip-or-hostname> scripts/press.sh "Amp Volume Up"
# Credentials come from the environment, never from the repo:
#   IR_HOST (required), IR_USER and IR_PASS (the web_server login from secrets.yaml)
set -euo pipefail
btn="${1:?usage: press.sh \"<button name>\"}"
: "${IR_HOST:?set IR_HOST to the bridge address}"
curl -fsS -X POST ${IR_USER:+--anyauth -u "$IR_USER:${IR_PASS:-}"} "http://${IR_HOST}/button/${btn// /%20}/press" && echo "sent: ${btn}"
