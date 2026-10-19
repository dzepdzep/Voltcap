package com.shelf.launcher.ui

/**
 * Update 246 (#60): a named section in a system trace (`atrace -a com.shelf.launcher view gfx`), so a slow frame can be
 * read part by part (which layout, the carousel, each game in it, its 3D case). Costs next to nothing while no trace
 * runs (android.os.Trace checks a flag). Not around anything that can return early or throw past it.
 */
internal inline fun <T> vcTrace(name: String, block: () -> T): T {
    android.os.Trace.beginSection(name)
    val r = block()
    android.os.Trace.endSection()
    return r
}
