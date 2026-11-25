package com.shelf.launcher.data

import java.io.File

/**
 * Native runtimes for the existing Switch / Switch 2 platforms (owner 25 Nov 23:41: "Peanuts Native: existing Nintendo Switch
 * platform. Banana Native: existing Nintendo Switch 2 platform ... selectable as alternative external runtimes for compatible
 * games, including per-game selection"). No platform of their own and no new game records: they are two more emulators in
 * [EmulatorCatalog] for "Switch" / "Switch2", picked per console or per game like any other.
 *
 * Their documented frontend entry points (read from the official sources, 25 Nov 2026):
 * - Banana Native (git.eden-emu.dev/moceure/BananaNative, android/FRONTENDS.md): release package `dev.bananative.android`,
 *   component `dev.bananative.android/dev.bananative.android.Launch` (an alias of FrontendLaunch with no intent filters).
 * - Peanuts Native (github.com/igawa6/peanuts-native, peanuts/docs/LAUNCHERS.md, release 0.1.8): release package
 *   `dev.nxnative.android.peanuts`, build-stable component `dev.peanuts.launch.LaunchActivity`.
 * Both take the extra `game` with the file's path ("the most reliable form"), a `.nup` (github.com/a55uka/nup) or a game
 * folder; anything that still needs the user (Prepare, shaders, All files access) opens the runtime's own Home.
 */
object NativeRuntimes {
    const val BANANA_PACKAGE = "dev.bananative.android"
    const val BANANA_ACTIVITY = "dev.bananative.android.Launch"
    const val PEANUTS_PACKAGE = "dev.nxnative.android.peanuts"
    const val PEANUTS_ACTIVITY = "dev.peanuts.launch.LaunchActivity"
    /** The extra both take with the game's path. */
    const val GAME_EXTRA = "game"
    /** Their single-file game format. */
    const val NUP = "nup"

    val PACKAGES = setOf(BANANA_PACKAGE, PEANUTS_PACKAGE)

    fun isNative(packageName: String?): Boolean = packageName != null && packageName in PACKAGES

    /**
     * The file a native runtime is given for a library game. A `.nup` or a game folder as it is; a game listed by its
     * `.nsp` / `.xci` (the record, its artwork and settings stay as they are) gets the `.nup` of the same name beside it
     * when there is one - the runtimes' frontend entry only opens `.nup` files and game folders. Else the path unchanged
     * (the runtime then says itself what it needs). Any other emulator: unchanged. [exists] is for tests.
     */
    fun romFor(packageName: String?, romPath: String, exists: (File) -> Boolean = { it.isFile }): String {
        if (!isNative(packageName)) return romPath
        val f = File(romPath)
        if (f.extension.equals(NUP, true) || f.isDirectory) return romPath
        val dir = f.parentFile ?: return romPath
        val base = f.nameWithoutExtension
        for (ext in listOf(NUP, NUP.uppercase())) {
            val nup = File(dir, "$base.$ext")
            if (exists(nup)) return nup.path
        }
        return romPath
    }

    /** True when a game's file can go to a native runtime as it is or through its `.nup` beside it. */
    fun compatible(romPath: String, exists: (File) -> Boolean = { it.isFile }): Boolean {
        val f = File(romPath)
        return f.extension.equals(NUP, true) || f.isDirectory ||
            romFor(PEANUTS_PACKAGE, romPath, exists) != romPath
    }
}
