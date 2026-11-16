#!/usr/bin/env bash
# 676d (owner decision 3A): the stable QA signing key. Never prints the key or its base64.
#   bash tools/cloud/qa_key.sh restore   - writes ~/.android/debug.keystore from $VOLTCAP_QA_DEBUG_KEYSTORE_B64 (the cloud
#                                          environment's private variable), mode 600, then checks it
#   bash tools/cloud/qa_key.sh check     - the keystore's certificate must be the pinned one (tools/qa/qa_signing.json);
#                                          exit 1 otherwise: a QA APK signed with another key can't update the installed QA
#                                          app without an uninstall (its settings lost) and breaks its Google sign-in.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KS="${HOME}/.android/debug.keystore"
PIN=$(python3 -c "import json;print(json.load(open('$ROOT/tools/qa/qa_signing.json'))['cert_sha256'])")
fp() { keytool -list -v -keystore "$KS" -storepass android -alias androiddebugkey 2>/dev/null | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f'; }
case "${1:-check}" in
  restore)
    if [ -z "${VOLTCAP_QA_DEBUG_KEYSTORE_B64:-}" ]; then echo "qa_key: VOLTCAP_QA_DEBUG_KEYSTORE_B64 is not set in this environment (nothing restored)"; exit 2; fi
    mkdir -p "$HOME/.android"; umask 077
    if [ -f "$KS" ] && [ "$(fp)" != "$PIN" ]; then mv "$KS" "$KS.other.$(date +%s)"; echo "qa_key: a different debug key was set aside"; fi
    printf '%s' "$VOLTCAP_QA_DEBUG_KEYSTORE_B64" | base64 -d > "$KS.tmp" && mv "$KS.tmp" "$KS"; chmod 600 "$KS"
    "$0" check ;;
  check)
    [ -f "$KS" ] || { echo "qa_key: no $KS - run 'restore' (the cloud environment needs VOLTCAP_QA_DEBUG_KEYSTORE_B64)"; exit 1; }
    got=$(fp)
    if [ "$got" = "$PIN" ]; then echo "qa_key: OK - the pinned QA key (${PIN:0:8}...)"; exit 0; fi
    echo "qa_key: WRONG KEY - this keystore is ${got:0:8}..., the QA app is signed with ${PIN:0:8}... Do not install a QA build made with it."; exit 1 ;;
  *) echo "usage: qa_key.sh restore|check"; exit 2 ;;
esac
