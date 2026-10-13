package com.shelf.launcher.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.util.concurrent.Executors

/**
 * Update 131: Voltcap's extra art packs, downloaded instead of built into the app:
 *  - [Pack.CARTS]: your cartridge pack (14 shapes × opaque / clear), one zip per console (`carts_<id>.zip`, the pack's
 *    ES-DE console id), unpacked to `files/packs/carts/<shape>/<o|t>/<id>.webp`;
 *  - [Pack.SPINES]: your console spine pack, one picture per console (`spine_<name>.webp` → `files/packs/spines/<name>.webp`);
 *  - [Pack.DISCS]: your optical-system pack, a shell for each disc console (`disc_<name>.webp` → `files/packs/discs/<name>.webp`);
 *  - [Pack.MODELS] (update 136): your 3D shape library pre-rendered from seven angles, one zip per model (`model_<id>.zip`),
 *    unpacked to `files/packs/models/<id>/` (ui/Models3D.kt); update 137: v6.8, and `model_logos.zip` (each console's
 *    logo and colours, `files/packs/models/logos/`).
 *
 * They come from the folder "packs/v1" of the public GitHub repo dzepdzep/Voltcap ([RELEASE]; update 139, was thebigpocket) into the app's own storage. At first
 * start Voltcap asks once (all, only the consoles in your library, or none); Settings › Game look › Extra art packs does
 * the same any time, and removes them. With "only my consoles", a console added to the library later gets its pieces at
 * the next start. Nothing here is needed: without a pack, the looks fall back to Voltcap's own drawn media / spines.
 */
object Packs {
    const val RELEASE = "https://raw.githubusercontent.com/dzepdzep/Voltcap/main/packs/v1/"

    enum class Pack(val key: String, val label: String, val about: String) {
        CARTS("carts", "Cartridges", "Your cartridge pack: 14 shapes, opaque or clear, for the Cartridges look"),
        SPINES("spines", "Console spines", "Your spine pack: a spine for each console on the shelves and the 3D boxes"),
        DISCS("discs", "Disc consoles", "Your optical-system pack: a shell for each disc console in the Cartridges look"),
        MODELS("models", "3D models", "Your 3D shape library: real and custom cartridges, cards and cases, from every side")
    }

    /** One downloadable piece: its [pack], the Voltcap console keys it serves, its [name] in the pack, its release file. */
    class Item(val pack: Pack, val name: String, val consoles: Set<String>) {
        val file: String get() = when (pack) {
            Pack.CARTS -> "carts_$name.zip"
            Pack.SPINES -> "spine_$name.webp"
            Pack.DISCS -> "disc_$name.webp"
            Pack.MODELS -> "model_$name.zip"
        }
    }

    /** The consoles in the library (the home screen keeps it up to date), for "only the consoles I have". */
    @Volatile var libraryConsoles: Set<String> = emptySet()

    /** The first-start choice (also the last one made in Settings). */
    enum class Choice { ALL, MINE, NONE }

    // ---- What there is ----

    /** Cartridge pack ids → the Voltcap consoles on them (the inverse of CustomCarts' table). */
    private val CART_IDS: Map<String, Set<String>> by lazy {
        com.shelf.launcher.ui.CustomCarts.allIds().entries.groupBy({ it.value }, { it.key }).mapValues { it.value.toSet() }
    }

    val items: List<Item> by lazy {
        CART_IDS.map { (id, consoles) -> Item(Pack.CARTS, id, consoles) } +
            com.shelf.launcher.ui.CustomSpines.packFiles().map { (name, consoles) -> Item(Pack.SPINES, name, consoles) } +
            com.shelf.launcher.ui.DiscShells.packFiles().map { (name, consoles) -> Item(Pack.DISCS, name, consoles) } +
            com.shelf.launcher.ui.Models3D.packFiles().map { (name, consoles) -> Item(Pack.MODELS, name, consoles) }
    }

    fun itemsOf(p: Pack): List<Item> = items.filter { it.pack == p }

    // ---- What is here ----

    private var appContext: Context? = null

    /** Bumped whenever pieces arrive or go, so every screen that draws them redraws (read it where they're drawn). */
    val version = mutableIntStateOf(0)

    /** The download in progress, or null. */
    data class Progress(val done: Int, val total: Int, val current: String, val failed: Int)
    val progress = mutableStateOf<Progress?>(null)

    /** The pieces on this device, by release file name. */
    @Volatile private var have: Set<String> = emptySet()

    fun dir(c: Context, p: Pack): File = File(c.filesDir, "packs/${p.key}")

    /** Reads what is on the device (main thread, quick: a few directory listings). Call once at start. */
    fun init(c: Context) {
        appContext = c.applicationContext
        com.shelf.launcher.ui.ModelColors.init(c.applicationContext)
        PhysicalOverrides.init(c.applicationContext)
        rescan(c.applicationContext)
        // (Update 166: what you had of the 3D pack, in its new version.)
        refreshOld(c.applicationContext)
    }

    private fun rescan(c: Context) {
        val found = HashSet<String>()
        for (it in items) if (marker(c, it).exists()) found += it.file
        have = found
        com.shelf.launcher.ui.Models3D.reset()
        version.intValue++
    }

    /** The file that says [item] is complete (a zip's marker, a picture itself). */
    private fun marker(c: Context, item: Item): File = when (item.pack) {
        Pack.CARTS -> File(dir(c, Pack.CARTS), "${item.name}.ok")
        Pack.SPINES -> File(dir(c, Pack.SPINES), "${item.name}.webp")
        Pack.DISCS -> File(dir(c, Pack.DISCS), "${item.name}.webp")
        // (Update 137: ".v68" — the pieces from before your library's v6.8 count as missing, so they're fetched again.
        // Update 166: ".v611" — v6.11 all the way round.)
        Pack.MODELS -> File(dir(c, Pack.MODELS), "${item.name}.v611")
    }

    fun has(item: Item): Boolean = item.file in have

    /** The cartridge pack's picture for console [id] in shape [shape] ([clear]: translucent), when it's on the device. */
    fun cartFile(id: String, shape: String, clear: Boolean): File? {
        version.intValue
        val c = appContext ?: return null
        if ("carts_$id.zip" !in have) return null
        return File(dir(c, Pack.CARTS), "$shape/${if (clear) "t" else "o"}/$id.webp")
    }

    /** Whether the cartridge pack's console [id] is on the device. */
    fun hasCart(id: String): Boolean { version.intValue; return "carts_$id.zip" in have }

    /** Whether any of the cartridge pack is on the device. */
    fun anyCarts(): Boolean { version.intValue; return have.any { it.startsWith("carts_") } }

    /** Your spine picture [name] when it's on the device. */
    fun spineFile(name: String): File? {
        version.intValue
        val c = appContext ?: return null
        return if ("spine_$name.webp" in have) File(dir(c, Pack.SPINES), "$name.webp") else null
    }

    /** Your disc-console shell [name] when it's on the device. */
    fun discFile(name: String): File? {
        version.intValue
        val c = appContext ?: return null
        return if ("disc_$name.webp" in have) File(dir(c, Pack.DISCS), "$name.webp") else null
    }

    /** Update 136: whether the 3D model [id] is on the device. */
    fun hasModel(id: String): Boolean { version.intValue; return "model_$id.zip" in have }

    /** Whether any of the 3D models are on the device. */
    fun anyModels(): Boolean { version.intValue; return have.any { it.startsWith("model_") } }

    /** The folder with the 3D model [id]'s pictures, when it's on the device. */
    fun modelDir(id: String): File? {
        val c = appContext ?: return null
        return if (hasModel(id)) File(dir(c, Pack.MODELS), id) else null
    }

    /** How many of [p]'s pieces are on the device, of how many. */
    fun count(p: Pack): Pair<Int, Int> { version.intValue; val all = itemsOf(p); return all.count { has(it) } to all.size }

    // ---- The choice ----

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("packs", Context.MODE_PRIVATE)

    /** The choice made, or null when Voltcap hasn't asked yet. */
    fun choice(c: Context): Choice? = prefs(c).getString("choice", null)?.let { n -> Choice.values().firstOrNull { it.name == n } }

    fun setChoice(c: Context, ch: Choice) { prefs(c).edit().putString("choice", ch.name).apply() }

    /** Asked already (the first-start question shows only while this is null). */
    val asked = mutableStateOf(true)

    fun loadAsked(c: Context) { asked.value = choice(c) != null }

    // ---- Downloading ----

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "packs").apply { isDaemon = true } }
    @Volatile private var stop = false

    /** What [ch] wants on the device for a library with [consoles] (Voltcap console keys). */
    fun wanted(ch: Choice, consoles: Set<String>): List<Item> = when (ch) {
        Choice.ALL -> items
        Choice.MINE -> items.filter { it.consoles.any { k -> k in consoles } }
        Choice.NONE -> emptyList()
    }

    /** Downloads what [ch] wants and isn't here yet ([consoles]: the library's consoles). Runs in the background. */
    fun download(c: Context, ch: Choice, consoles: Set<String>) {
        fetchAll(c.applicationContext, wanted(ch, consoles).filter { !has(it) })
    }

    /**
     * Update 166: the 3D pieces you had from before (their old marker, e.g. "<id>.v68") fetched again in their new
     * version by themselves, in the background, at start — after 166 the 3D looks showed the flat looks until you
     * downloaded the pack again by hand.
     */
    fun refreshOld(c: Context) {
        val app = c.applicationContext
        val d = dir(app, Pack.MODELS)
        val old = itemsOf(Pack.MODELS).filter { !has(it) && OLD_MODEL_MARKERS.any { m -> File(d, "${it.name}$m").exists() } }
        fetchAll(app, old)
    }

    /** Update 166: the markers of the 3D pack's earlier versions. */
    private val OLD_MODEL_MARKERS = listOf(".v68")

    private fun fetchAll(app: Context, todo: List<Item>) {
        if (todo.isEmpty() || progress.value != null) return
        stop = false
        progress.value = Progress(0, todo.size, todo.first().pack.label, 0)
        worker.execute {
            var failed = 0
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            todo.forEachIndexed { i, item ->
                if (stop) return@forEachIndexed
                progress.value = Progress(i, todo.size, item.pack.label, failed)
                if (!runCatching { fetch(app, client, item) }.getOrDefault(false)) failed++
                // Each piece shows as soon as it's here.
                have = have + (if (marker(app, item).exists()) setOf(item.file) else emptySet())
                version.intValue++
            }
            rescan(app)
            progress.value = null
            lastFailed.value = failed
        }
    }

    /** How many pieces failed in the last download (0 = all fine, null = none ran yet). */
    val lastFailed = mutableStateOf<Int?>(null)

    /** Stops the running download after the piece it's on. */
    fun cancel() { stop = true }

    private fun fetch(c: Context, client: okhttp3.OkHttpClient, item: Item): Boolean {
        val req = okhttp3.Request.Builder().url(RELEASE + item.file).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return false
            val body = res.body ?: return false
            val dir = dir(c, item.pack).apply { mkdirs() }
            val tmp = File(dir, "${item.file}.part")
            tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            return when (item.pack) {
                Pack.CARTS, Pack.MODELS -> {
                    // Unpacked where the pictures live; entries that would land outside the folder are skipped.
                    val root = dir.canonicalFile
                    java.util.zip.ZipInputStream(tmp.inputStream().buffered()).use { zip ->
                        while (true) {
                            val e = zip.nextEntry ?: break
                            val out = File(dir, e.name).canonicalFile
                            if (!out.path.startsWith(root.path + File.separator) || e.isDirectory) continue
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    tmp.delete()
                    marker(c, item).writeText("1")
                    true
                }
                else -> tmp.renameTo(marker(c, item))
            }
        }
    }

    /** Removes every pack from the device. */
    fun removeAll(c: Context) {
        val app = c.applicationContext
        stop = true // a running download stops after the piece it's on; the removal runs right after it
        worker.execute {
            Pack.values().forEach { dir(app, it).deleteRecursively() }
            rescan(app)
        }
    }

    /** Roughly how much the packs take on the device, in MB (blocking: call off the main thread). */
    fun sizeMb(c: Context): Int = (Pack.values().sumOf { p -> dir(c, p).walkTopDown().filter { it.isFile }.sumOf { it.length() } } / 1_000_000L).toInt()
}
