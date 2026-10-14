package com.shelf.launcher.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.zip.ZipInputStream

/*
 * Update 177: pictures for the console folders on the second screen's Games page. Three places, first found wins:
 * 1. a picture picked for one console (Settings › Screens › Second screen Games page › Folder icons › Change; update 200:
 *    also a long-press on the folder on the Games page › Change icon…; animated GIF / WebP ones play, FolderIcons.kt);
 * 2. a folder pack: a .zip (or the Voltcap/Folders folder) of pictures named after the consoles;
 * 3. none: the drawn folder / tile in the theme's colours with the console's logo.
 *
 * The naming rule for a pack (the same names as ROM folders, so most packs made for other frontends just work):
 * - the console's folder name: snes.png, psx.png, gba.webp, "Game Boy Advance.png", megadrive.jpg… (any name a ROM folder
 *   can have for that console — RomScanner.platformForFolderName);
 * - iiSU-style names too: <name>_list.png, <name>_list_selected.png, <name>_title.png, <name>_icon.png (the plain name or
 *   _list is used first);
 * - _default.png (or default.png): for every console without its own.
 * PNG, JPG, WebP or GIF; folders inside the zip are fine (only the file names count).
 */
object FolderArt {
    /** Raised when pictures are added or removed (the Games page and Settings redraw). */
    var version by mutableIntStateOf(0)

    /** The consoles there are (set by the home screen), for Settings. */
    var platforms by mutableStateOf<List<String>>(emptyList())

    const val DROP_FOLDER = "Voltcap/Folders"
    private val PICTURES = setOf("png", "jpg", "jpeg", "webp", "gif")
    private const val MAX_BYTES = 64L * 1024 * 1024
    private const val MAX_FILES = 400

    private fun root(context: Context) = File(context.filesDir, "folder_art")
    private fun packDir(context: Context) = File(root(context), "pack")
    private fun mineDir(context: Context) = File(root(context), "mine")

    @Volatile private var index: Map<String, File>? = null
    @Volatile private var indexedAt = -1
    @Volatile private var fallback: File? = null

    /** The picture for [platform]'s folder, or null (the drawn one). */
    fun find(context: Context, platform: String): File? {
        mineDir(context).listFiles()?.firstOrNull { it.nameWithoutExtension == safe(platform) && it.isFile }?.let { return it }
        if (indexedAt != version || index == null) build(context)
        return index?.get(platform) ?: fallback
    }

    /** How many consoles the pack has a picture for (and whether it has a default one). */
    fun packSummary(context: Context): Pair<Int, Boolean> {
        if (indexedAt != version || index == null) build(context)
        return (index?.size ?: 0) to (fallback != null)
    }

    /** Whether [platform] has a picture of its own picked. */
    fun hasMine(context: Context, platform: String): Boolean =
        mineDir(context).listFiles()?.any { it.nameWithoutExtension == safe(platform) } == true

    private fun build(context: Context) {
        val files = packDir(context).walkTopDown().filter { it.isFile && it.extension.lowercase() in PICTURES }.toList()
        val map = HashMap<String, Pair<Int, File>>()
        var def: File? = null
        for (f in files) {
            val base = f.nameWithoutExtension.lowercase()
            if (base == "_default" || base == "default") { def = f; continue }
            // iiSU-style suffixes: the plain name and _list come first.
            val (name, rank) = when {
                base.endsWith("_list_selected") -> base.removeSuffix("_list_selected") to 2
                base.endsWith("_list") -> base.removeSuffix("_list") to 1
                base.endsWith("_title") -> base.removeSuffix("_title") to 3
                base.endsWith("_icon") -> base.removeSuffix("_icon") to 4
                else -> base to 0
            }
            val platform = RomScanner.platformForFolderName(name) ?: platforms.firstOrNull { it.equals(name, true) } ?: continue
            val had = map[platform]
            if (had == null || rank < had.first) map[platform] = rank to f
        }
        index = map.mapValues { it.value.second }
        fallback = def
        indexedAt = version
    }

    private fun safe(platform: String) = platform.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** Imports a folder pack (a .zip of pictures). Replaces the pack before it. */
    fun importZip(context: Context, uri: Uri): String = try {
        val dir = packDir(context)
        val tmp = File(root(context), "pack_new").apply { deleteRecursively(); mkdirs() }
        var total = 0L
        var count = 0
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                while (e != null && count < MAX_FILES) {
                    val name = e.name.substringAfterLast('/').substringAfterLast('\\')
                    if (!e.isDirectory && name.substringAfterLast('.', "").lowercase() in PICTURES && !name.startsWith(".")) {
                        val bytes = zip.readBytes()
                        total += bytes.size
                        if (total > MAX_BYTES) break
                        File(tmp, name).writeBytes(bytes)
                        count++
                    }
                    e = zip.nextEntry
                }
            }
        }
        if (count == 0) { tmp.deleteRecursively(); "No pictures in that file (PNG, JPG, WebP or GIF)." }
        else {
            dir.deleteRecursively(); tmp.renameTo(dir); version++
            val (n, def) = packSummary(context)
            "Pack imported: pictures for $n consoles" + (if (def) ", and a default one." else ".") +
                if (total > MAX_BYTES) " (Stopped at ${MAX_BYTES / 1024 / 1024} MB.)" else ""
        }
    } catch (e: Exception) { "Couldn't import it: ${e.message ?: e::class.java.simpleName}" }

    /** Copies the pictures in the Voltcap/Folders folder as the pack. */
    fun importFolder(context: Context): String {
        val base = File(android.os.Environment.getExternalStorageDirectory(), DROP_FOLDER)
        if (!base.isDirectory) {
            return if (AppStorage.canUseRootFolder() && base.mkdirs()) "Made the $DROP_FOLDER folder — put the pictures in it and tap this again."
            else "Make a folder named $DROP_FOLDER on the handheld's storage and put the pictures in it."
        }
        val files = base.walkTopDown().filter { it.isFile && it.extension.lowercase() in PICTURES }.take(MAX_FILES).toList()
        if (files.isEmpty()) return "No pictures in $DROP_FOLDER."
        val dir = packDir(context).apply { deleteRecursively(); mkdirs() }
        files.forEach { f -> runCatching { f.copyTo(File(dir, f.name), overwrite = true) } }
        version++
        val (n, def) = packSummary(context)
        return "Pictures for $n consoles" + (if (def) ", and a default one." else ".")
    }

    fun removePack(context: Context) {
        packDir(context).deleteRecursively()
        version++
    }

    /** A picture picked for one console. */
    fun setMine(context: Context, platform: String, uri: Uri): Boolean = try {
        val dir = mineDir(context).apply { mkdirs() }
        dir.listFiles()?.filter { it.nameWithoutExtension == safe(platform) }?.forEach { it.delete() }
        val ext = context.contentResolver.getType(uri)?.substringAfter('/')?.takeIf { it in PICTURES } ?: "png"
        context.contentResolver.openInputStream(uri)?.use { input -> File(dir, "${safe(platform)}.$ext").outputStream().use { input.copyTo(it) } }
        version++
        true
    } catch (e: Exception) { false }

    fun clearMine(context: Context, platform: String) {
        mineDir(context).listFiles()?.filter { it.nameWithoutExtension == safe(platform) }?.forEach { it.delete() }
        version++
    }
}
