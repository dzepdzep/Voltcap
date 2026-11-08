# Supervised device QA without Drive sync (D295, owner option C) — update 498

Why the order matters: v0.6.67 has no QA gate. Its launch sync runs ~5 s after every start (and on resume), the periodic
worker and weekly auto-backup run in the background. Every one of them is skipped when sync is switched off in Voltcap's own
settings (`CloudSync.isActive` = connected AND (saves OR launcher data); `CloudSyncWorker.schedule` cancels; `AutoBackupWorker`
returns at OFF). So the switches must be off BEFORE anything restarts Voltcap — never install first and switch off after.

**Owner, on the handheld, with the v0.6.67 already running (no restart, no install):**
1. Settings › Sync (Google Drive): switch OFF "Sync game saves" and "Sync favourites and playtime" (stay signed in — don't disconnect).
   Do not press "Sync now". Do not open the Save Vault (it lists Drive-only saves while signed in).
2. Settings › Backup › Automatic backup to Google Drive: Off.
3. Note the two original values; tell Claude "device prepared" with the serial.

**Claude (only after that message):**
4. Confirm USB + serial; read-only check that Voltcap is in the foreground and the screen is on. Snapshot (setguard / viewstate save).
5. Install the QA build, create the hooks file, force-stop + start (second layer: `CloudSync.qaPaused`, log line
   `VoltcapQA sync paused (QA session)` must appear; any `sync`/`backup` line without it = stop the run).
6. Tests (launch block stays on; no game). Snapshot compare (D293) — only intended test writes.
7. Remove the hooks file, install v0.6.67 back (`install -r`, md5 check). Its start finds sync off → no sync, no backup.
8. Snapshot = base. Hand back.

**Owner afterwards:** switch the two sync switches and Automatic backup back to your values. Turning sync on starts a normal
sync — that is your own action, as in everyday use.

If step 1–2 can't be done first (e.g. the device is on a QA build that will be replaced), stop: ask the owner.
