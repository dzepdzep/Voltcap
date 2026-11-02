# Frozen release candidates

Gradle writes every build to `app/build/outputs/apk/<variant>/Voltcap-<version>-<variant>.apk` — the same name each
time — so a staging build overwrote the signed v0.6.66 RC (update 456). A release candidate is now frozen here right
after it is built:

    python tools/release/freeze_rc.py <version> <label> [note]     e.g.  0.6.67 rc1
    python tools/release/freeze_rc.py --verify

- `<version>/Voltcap-<version>-<label>-<variant>-<sha8>.apk` (+ `.dm` profiles), read-only; a label is never reused.
- `SHA256SUMS.txt` (committed) holds every frozen file's checksum; the APKs are git-ignored (too big for git).
- Gradle never writes here, so later or staging builds can't replace a frozen candidate.
- Publish the frozen file (attach it to the GitHub release); never a rebuild described as the RC.
- 0.6.66: only a REBUILD of the unchanged frozen source is kept (`rebuild1`); the original RC file is lost — see SHA256SUMS.txt.
