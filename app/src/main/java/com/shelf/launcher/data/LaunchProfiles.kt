package com.shelf.launcher.data

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * How to start a game in a specific standalone emulator: which activity to
 * target, and where the game goes (an intent extra, or the intent's data).
 * Modelled on the launch commands ES-DE uses on Android. Applied automatically
 * when an emulator is picked for a console without a custom activity set.
 *
 * Games are handed over as a link through Shelf's own file provider (with read
 * access granted to the emulator) unless a profile says otherwise. Storage
 * links rooted at the whole storage only open in an emulator that was given
 * that exact folder, which is why they failed for DuckStation, Dolphin & co.
 */
data class LaunchProfile(
    val packages: List<String>,
    /** Activities to try, in order; ones the installed build lacks are skipped.
     * Empty = let Android pick (VIEW) or use the emulator's own start screen. */
    val activities: List<String>,
    val action: String = Intent.ACTION_MAIN,
    /** Intent extra that carries the game; null = pass it as the intent's data. */
    val romExtra: String?,
    val format: RomFormat,
    /** Also put the game on the intent's data, alongside the extra. */
    val alsoAsData: Boolean = false,
    val mimeType: String? = null,
    val boolExtras: Map<String, Boolean> = emptyMap()
) {
    /** The main activity, for places that show or pre-fill one. */
    val activity: String? get() = activities.firstOrNull()
}

/**
 * Sources: ES-DE's Android find rules and launch commands, and NeoStation's
 * published launch arguments (github.com/idarius/neostation-systems), which
 * are tested `am start` commands.
 */
object LaunchProfiles {

    private val profiles = listOf(
        // PlayStation
        LaunchProfile(
            listOf("com.github.stenzek.duckstation"),
            listOf("com.github.stenzek.duckstation.EmulationActivity"),
            romExtra = "bootPath", format = RomFormat.SHARED_URI,
            boolExtras = mapOf("resumeState" to false)
        ),
        // PlayStation 2 — AetherSX2 / NetherSX2 (+ Turnip builds): path in bootPath
        LaunchProfile(
            listOf("xyz.aethersx2.android", "xyz.aethersx2.tturnip", "xyz.aethersx2.cturnip"),
            listOf("xyz.aethersx2.android.EmulationActivity"),
            romExtra = "bootPath", format = RomFormat.FILE_PATH
        ),
        // PlayStation 2 — ARMSX2 (Play Store build and Refresh builds). ES-DE
        // opens it with a plain "view this file" request; bootPath rides along
        // for builds that read it instead.
        LaunchProfile(
            listOf("come.nanodata.armsx2", "com.armsx2", "com.nanodata.armsx2"),
            listOf(
                "com.armsx2.Main",
                "kr.co.iefriends.pcsx2.activities.MainActivity",
                "kr.co.iefriends.pcsx2.MainActivity"
            ),
            action = Intent.ACTION_VIEW,
            romExtra = "bootPath", format = RomFormat.SHARED_URI, alsoAsData = true
        ),
        // GameCube / Wii — Dolphin and forks share the code
        LaunchProfile(
            listOf(
                "org.dolphinemu.dolphinemu", "org.dolphinemu.mmjr", "org.dolphinemu.dolphinemu.debug",
                "org.mm.j", "org.dolphinemu.primehack", "org.shiiion.primehack"
            ),
            listOf("org.dolphinemu.dolphinemu.ui.main.MainActivity"),
            romExtra = "AutoStartFile", format = RomFormat.SHARED_URI
        ),
        // PSP
        LaunchProfile(
            listOf("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp"),
            listOf("org.ppsspp.ppsspp.PpssppActivity"),
            action = Intent.ACTION_VIEW, romExtra = null, format = RomFormat.SHARED_URI,
            mimeType = "application/octet-stream"
        ),
        // Nintendo DS
        LaunchProfile(
            listOf("me.magnum.melonds", "me.magnum.melondualds", "me.magnum.melonds.dev"),
            listOf("me.magnum.melonds.ui.emulator.EmulatorActivity"),
            action = "me.magnum.melonds.LAUNCH_ROM", romExtra = null, format = RomFormat.SHARED_URI
        ),
        LaunchProfile(
            listOf("com.dsemu.drastic"),
            listOf("com.dsemu.drastic.DraSticActivity"),
            romExtra = "GAMEPATH", format = RomFormat.FILE_PATH
        ),
        // Nintendo 3DS
        LaunchProfile(
            listOf("org.citra.citra_emu"),
            listOf("org.citra.citra_emu.activities.EmulationActivity"),
            romExtra = "SelectedGame", format = RomFormat.SHARED_URI
        ),
        LaunchProfile(
            listOf("io.github.lime3ds.android"),
            listOf("io.github.lime3ds.android.activities.EmulationActivity"),
            romExtra = "SelectedGame", format = RomFormat.SHARED_URI
        ),
        LaunchProfile(
            listOf("org.azahar_emu.azahar"),
            listOf("org.azahar_emu.azahar.activities.EmulationActivity"),
            romExtra = "SelectedGame", format = RomFormat.SHARED_URI
        ),
        // Dreamcast
        LaunchProfile(
            listOf("io.recompiled.redream"),
            listOf("io.recompiled.redream.MainActivity"),
            action = Intent.ACTION_VIEW, romExtra = null, format = RomFormat.SHARED_URI
        ),
        LaunchProfile(
            listOf("com.flycast.emulator"),
            listOf("com.flycast.emulator.MainActivity"),
            action = Intent.ACTION_VIEW, romExtra = null, format = RomFormat.SHARED_URI
        ),
        // Nintendo 64
        LaunchProfile(
            listOf("org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.fzurita.pro"),
            listOf("paulscode.android.mupen64plusae.SplashActivity"),
            action = Intent.ACTION_VIEW, romExtra = null, format = RomFormat.SHARED_URI
        ),
    )

    fun find(packageName: String?): LaunchProfile? =
        packageName?.let { pkg -> profiles.firstOrNull { pkg in it.packages } ?: EmulatorCatalog.profileFor(pkg) }

    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * An Android storage (SAF) link for a file, rooted at [accessFolder] — the
     * folder the emulator was given access to inside its own settings. Android
     * only lets the emulator open the link when that root matches the folder
     * it was given *exactly*, which is why that folder is a setting (Start ›
     * Emulator › Folder given to the emulator). If the game isn't inside it,
     * the link is rooted at the storage root instead.
     */
    fun documentUri(path: String, accessFolder: String?): Uri? {
        val document = documentId(path) ?: return null
        val rootId = accessFolder?.let { documentId(it.trimEnd('/')) }
        val tree = when {
            rootId != null && (document == rootId || document.startsWith(rootId.trimEnd('/') + "/")) -> rootId
            else -> document.substringBefore(':') + ":"
        }
        val treeUri = DocumentsContract.buildTreeDocumentUri(STORAGE_AUTHORITY, tree)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, document)
    }

    /** "/storage/emulated/0/ROMs/psx/a.chd" → "primary:ROMs/psx/a.chd";
     * "/storage/1234-ABCD/ROMs/a.chd" → "1234-ABCD:ROMs/a.chd". */
    private fun documentId(path: String): String? {
        val clean = path.replace("//", "/")
        for (prefix in listOf("/storage/emulated/0/", "/sdcard/", "/storage/self/primary/")) {
            if (clean.startsWith(prefix)) return "primary:" + clean.removePrefix(prefix)
            if (clean == prefix.trimEnd('/')) return "primary:"
        }
        if (clean.startsWith("/storage/")) {
            val rest = clean.removePrefix("/storage/")
            val volume = rest.substringBefore('/')
            if (volume.isNotEmpty() && volume != "emulated") return "$volume:" + rest.substringAfter('/', "")
        }
        return null
    }
}
