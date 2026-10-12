package com.shelf.launcher.data.crash

/**
 * Scrubs secrets out of crash text before it is written to disk or exported.
 * Crash reports get sent to other people, so anything that looks like a
 * credential is replaced with a marker; a false positive only costs a little
 * readability, a miss would leak an account.
 *
 * Examples (input -> output):
 *  - `apiKey=abc123def`                        -> `apiKey=<redacted>`
 *  - `{"access_token":"ya29.a0Af","x":1}`      -> `{"access_token":<redacted>,"x":1}`
 *  - `Authorization: Bearer eyJhbGciOi...`     -> `Authorization: <redacted> <redacted>`
 *  - `GET https://x.tv/v1?key=SECRET&page=2`   -> `GET https://x.tv/v1?key=<redacted>&page=2`
 *  - `https://user:pw@host/path`               -> `https://<redacted>@host/path`
 *  - `Failed for jane.doe@example.com`         -> `Failed for <email>`
 *  - `stableKey=snes/zelda` (not a secret)     -> unchanged
 *
 * SharedPreferences contents are never put into reports in the first place;
 * this is the second line of defence for text that came from exception
 * messages, URLs and logcat.
 */
object CrashRedactor {
    const val MARK = "<redacted>"

    // Names whose value is secret. Suffix/prefix forms (sgdbApiKey, refresh_token,
    // ss_password …) are covered by the wildcard parts; a bare "key" needs a word
    // boundary so that stableKey / hotkey / monkey are left alone.
    private const val SECRET_NAME =
        "(?:[A-Za-z0-9_.-]*(?:api[_-]?key|token|secret|passw(?:or)?d|pwd|passphrase|authorization|" +
            "bearer|ssid|sspassword|devpassword|credential|cookie|session[_-]?id)[A-Za-z0-9_.-]*|code[_-]?verifier|code[_-]?challenge|client[_-]?secret|state|key|auth|sig|signature)"

    // name [quote] (= | :) value — value is quoted text or a run up to a separator.
    private val keyValue = Regex(
        "(?i)(?<![A-Za-z0-9])(\"?'?$SECRET_NAME\"?'?)(\\s*[=:]\\s*)(\"[^\"\\n]*\"|'[^'\\n]*'|[^\\s,;&\"'}\\])]+)"
    )

    // Query parameters in any URL-like text.
    private val queryParam = Regex(
        "(?i)([?&]$SECRET_NAME=)([^&\\s\"'#<>]+)"
    )

    private val bearer = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,}")
    private val urlUserInfo = Regex("(?i)(https?|ftp|smb|wss?)://[^/\\s:@]+:[^/\\s@]+@")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")

    fun redact(text: String): String {
        if (text.isEmpty()) return text
        return runCatching {
            var t = text
            t = urlUserInfo.replace(t) { "${it.groupValues[1]}://$MARK@" }
            t = bearer.replace(t) { "${it.groupValues[1]} $MARK" }
            t = jwt.replace(t, MARK)
            t = queryParam.replace(t) { "${it.groupValues[1]}$MARK" }
            t = keyValue.replace(t) { m ->
                val value = m.groupValues[3]
                // Kept as it is: already scrubbed, a stack frame (TokenStore.kt:88), or
                // an exception class name (InvalidTokenException: message).
                val name = m.groupValues[1]
                val frame = value.all { it.isDigit() } && (name.endsWith(".kt") || name.endsWith(".java"))
                val className = name.endsWith("Exception") || name.endsWith("Error")
                if (value.contains(MARK) || frame || className) m.value else "${m.groupValues[1]}${m.groupValues[2]}$MARK"
            }
            email.replace(t, "<email>")
        }.getOrDefault("[report text withheld: redaction failed]")
    }
}
