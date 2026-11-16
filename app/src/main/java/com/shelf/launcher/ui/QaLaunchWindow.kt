package com.shelf.launcher.ui

/**
 * 676n (owner 16 Nov, "HARDWARE TESTING AUTHORIZATION"): the isolated QA app may really open a harmless installed app - to check on
 * which of the Thor's screens it lands - only through this window. A QA script writes `<pkg>[,<pkg>...] <unix seconds until>` into
 * <external files>/qa_allow_app_launch; the window is honoured only on the plain-app path ("vm.app", "launcher.app"), only for the
 * packages listed and only while its end is in the future and at most [MAX_SECONDS] ahead. Games, ROMs, resume and every other path
 * stay blocked (Update 500), whatever the file says. The script removes the file when it ends; an old file expires on its own.
 */
internal object QaLaunchWindow {
    const val MAX_SECONDS = 900L
    private val PLAIN_APP = setOf("vm.app", "launcher.app")

    fun allows(where: String, pkg: String, file: java.io.File?, nowSec: Long = System.currentTimeMillis() / 1000): Boolean {
        if (where !in PLAIN_APP || file == null || !file.isFile) return false
        val text = runCatching { file.readText() }.getOrNull() ?: return false
        return parse(text, where, pkg, nowSec)
    }

    internal fun parse(text: String, where: String, pkg: String, nowSec: Long): Boolean {
        if (where !in PLAIN_APP) return false
        val parts = text.trim().split(Regex("\\s+"))
        if (parts.size != 2) return false
        val until = parts[1].toLongOrNull() ?: return false
        if (until <= nowSec || until - nowSec > MAX_SECONDS) return false
        return pkg.isNotEmpty() && pkg in parts[0].split(',').map { it.trim() }
    }
}
