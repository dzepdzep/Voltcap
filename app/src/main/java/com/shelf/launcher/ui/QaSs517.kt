package com.shelf.launcher.ui

import android.content.Context
import com.shelf.launcher.LauncherViewModel
import com.shelf.launcher.data.Game
import com.shelf.launcher.data.MetadataSource
import com.shelf.launcher.data.ScreenScraperCredentials
import com.shelf.launcher.data.ScreenScraperStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/**
 * Batch 517 (security review, QA app only): where ScreenScraper's answer puts credentials, and where they end up.
 * `--es voltcap_test_ss517 "Super Mario World"` (the hooks file must exist; the ordinary Voltcap never runs it).
 *
 * Writes <external files>/qa_ss517.txt with **names and counts only** — never a credential or a whole address:
 *  1. one real jeuInfos answer: every media / manual address grouped by path and the NAMES of its query parameters, and
 *     whether each parameter's value equals this app's developer ID / developer password / the account's password;
 *  2. the game's normal look-ups run through the app's own code (details + art, scans, clip, manual, a picked cover);
 *  3. every file in the app's private and external folders searched for those values and for "devpassword";
 *  4. the backups Voltcap writes (Export with / without game data, the text sent to Drive / nearby) searched the same way,
 *     built in memory or in the cache folder and deleted — nothing is uploaded (Drive sync is paused in the QA app).
 */
internal object QaSs517 {
    private class Needles(val devId: String, val devPw: String, val userPw: String?) {
        fun count(text: String): Map<String, Int> = buildMap {
            put("devpw_value", occurrences(text, devPw))
            put("devid_value", occurrences(text, devId))
            put("devpassword_key", occurrences(text, "devpassword"))
            put("sspassword_key", occurrences(text, "sspassword"))
            put("credential_param_with_value", Regex("""(?:[?&]|\\u0026|\\u003f)(devpassword|sspassword|devid|ssid)(?:=|\\u003d)(?!&|\\u0026|"|\s|$)""", RegexOption.IGNORE_CASE).findAll(text).count())
            userPw?.takeIf { it.length >= 4 }?.let { put("userpw_value", occurrences(text, it)) }
        }
    }

    private fun occurrences(hay: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var n = 0; var i = hay.indexOf(needle)
        while (i >= 0) { n++; i = hay.indexOf(needle, i + needle.length) }
        return n
    }

    private fun fmt(m: Map<String, Int>) = m.entries.joinToString(" ") { "${it.key}=${it.value}" }

    /** For the QA app only. */
    suspend fun run(context: Context, vm: LauncherViewModel, games: () -> List<Game>, art: () -> Map<String, com.shelf.launcher.data.GameArt>, query: String) {
        if (!com.shelf.launcher.data.QaVariant.isolated(context)) return
        val out = StringBuilder()
        val app = context.applicationContext
        val report = File(app.getExternalFilesDir(null), "qa_ss517.txt")
        fun line(s: String) { out.append(s).append('\n'); android.util.Log.e("VoltcapTest", "ss517 $s") }
        try {
            val store = ScreenScraperStore(app)
            // Batch 518 (direct-key download test): a key file pushed by the QA run is saved as a developer key "you entered"
            // (the QA app's own encrypted store) and deleted at once; the store is cleared again at the end of the run.
            val keyFile = File(app.getExternalFilesDir(null), "qa_ss_key.properties")
            var usedKeyFile = false
            if (keyFile.isFile) withContext(Dispatchers.IO) {
                val p = java.util.Properties().apply { keyFile.inputStream().use { load(it) } }
                keyFile.delete()
                val id = p.getProperty("devId").orEmpty().trim(); val pw = p.getProperty("devPassword").orEmpty().trim()
                if (id.isNotEmpty() && pw.isNotEmpty()) { store.save(ScreenScraperCredentials(id, pw, null, null)); usedKeyFile = true }
                line("key_file_used=$usedKeyFile key_file_deleted=${!keyFile.exists()}")
            }
            // Batch 518: a Twitch key file (as someone would type their own Client ID / Secret) — the direct IGDB path, whose
            // token request now sends the secret in the form body. Deleted at once; nothing is saved.
            val igdbFile = File(app.getExternalFilesDir(null), "qa_igdb_key.properties")
            if (igdbFile.isFile) withContext(Dispatchers.IO) {
                val p = java.util.Properties().apply { igdbFile.inputStream().use { load(it) } }
                igdbFile.delete()
                val repo = com.shelf.launcher.data.IgdbRepository(com.shelf.launcher.data.createIgdbApi())
                val id = p.getProperty("clientId").orEmpty().trim(); val sec = p.getProperty("clientSecret").orEmpty().trim()
                val ok = runCatching { repo.testConnection(id, sec) }.getOrDefault(false)
                val meta = runCatching { repo.fetchMetadata(id, sec, "Super Mario World", "SNES") }.getOrNull()
                val bad = runCatching { repo.testConnection(id, "wrong-secret-for-test") }.getOrDefault(true)
                line("igdb_own_key file_deleted=${!igdbFile.exists()} token_ok=$ok title_found=${meta?.title != null} summary=${meta?.summary != null} wrong_secret_refused=${!bad}")
            }
            val svc = com.shelf.launcher.data.VoltcapService
            // Batch 519 (production): the account the owner entered in this QA app's ScreenScraper dialog is used as it is.
            if (svc.configured) withContext(Dispatchers.IO) {
                line("service host=${svc.baseUrl?.host}")
                val saved = store.load()
                val account = saved?.takeIf { svc.isService(it) && !it.username.isNullOrBlank() }
                val repo = com.shelf.launcher.data.ScreenScraperRepository(com.shelf.launcher.data.createScreenScraperApi())
                if (account == null) line("service ss_account=none (enter it in Settings > Accounts > ScreenScraper)")
                else {
                    val err = repo.testConnection(account)
                    val wrong = repo.testConnection(account.copy(password = "wrong-password-for-test"))
                    line("service ss_login_ok=${err == null} ${err?.take(100).orEmpty()} wrong_login_message=${wrong?.take(80)}")
                }
                val ig = com.shelf.launcher.data.IgdbRepository(com.shelf.launcher.data.createIgdbApi())
                val on = runCatching { ig.testConnection(svc.SERVICE_ID, svc.SERVICE_ID) }.getOrDefault(false)
                val meta = runCatching { ig.fetchMetadata(svc.SERVICE_ID, svc.SERVICE_ID, "Super Mario World", "SNES") }.getOrNull()
                line("service igdb_turn_on_ok=$on title=${meta?.title} summary_found=${meta?.summary != null} critic=${meta?.criticScore} hours=${meta?.normallyHours}")
            }
            var creds = withContext(Dispatchers.IO) { store.load() }
            line("store_connected_before=${creds != null}")
            if (creds == null) {
                // The QA app's own private store: the built-in developer key (saved blank), no account.
                withContext(Dispatchers.IO) { store.save(ScreenScraperCredentials("", "", null, null)) }
                creds = withContext(Dispatchers.IO) { store.load() }
                line("store_connected_now=${creds != null} (built-in key, no account)")
            }
            // (Batch 517 hotfix builds have no developer key: parts 1-2 are skipped and parts 3-4 count the credential
            // parameters themselves — "devpassword=<something>" etc. — instead of values this build doesn't know.)
            val c = creds?.takeIf { it.devId != com.shelf.launcher.data.VoltcapService.SERVICE_ID }
            line("developer_key_in_app=${c != null} account_set=${!creds?.username.isNullOrBlank()}")
            val needles = Needles(c?.devId.orEmpty(), c?.devPassword.orEmpty(), creds?.password)

            // ---- 1. one real answer ----
            if (c != null) withContext(Dispatchers.IO) {
                val params = mutableMapOf(
                    "devid" to c.devId, "devpassword" to c.devPassword,
                    "softname" to com.shelf.launcher.data.ScreenScraperDevKey.softName, "output" to "json",
                    "systemeid" to "4", "romtype" to "rom", "romnom" to "Super Mario World (USA).sfc"
                )
                if (!c.username.isNullOrBlank() && !c.password.isNullOrBlank()) { params["ssid"] = c.username; params["sspassword"] = c.password }
                val r = com.shelf.launcher.data.createScreenScraperApi().gameInfo(params)
                val body = r.body()?.string() ?: r.errorBody()?.string().orEmpty()
                line("== 1. jeuInfos http=${r.code()} bodyLen=${body.length}")
                line("whole_body ${fmt(needles.count(body))}")
                val urls = Regex("\"url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(body).map { it.groupValues[1].replace("\\/", "/") }.toList()
                line("addresses=${urls.size}")
                data class G(val host: String, val path: String, val names: String)
                val groups = linkedMapOf<G, MutableList<String>>()
                for (u in urls) {
                    val h = u.toHttpUrlOrNull()
                    if (h == null) { groups.getOrPut(G("?", "?", "unparseable"), ::mutableListOf) += u; continue }
                    // The host's first label only (no full address); the path's last part.
                    groups.getOrPut(G(h.host.substringAfter('.'), h.encodedPath.substringAfterLast('/'), h.queryParameterNames.sorted().joinToString(",")), ::mutableListOf) += u
                }
                for ((g, list) in groups) {
                    fun eq(name: String, v: String?) = if (v.isNullOrEmpty()) 0 else list.count { it.toHttpUrlOrNull()?.queryParameter(name) == v }
                    fun nonEmpty(name: String) = list.count { !it.toHttpUrlOrNull()?.queryParameter(name).isNullOrEmpty() }
                    line("group host=*.${g.host} path=${g.path} n=${list.size} params=[${g.names}] " +
                        "devid=app_value:${eq("devid", c.devId)} devpassword=app_value:${eq("devpassword", c.devPassword)} " +
                        "ssid=nonempty:${nonEmpty("ssid")} sspassword=nonempty:${nonEmpty("sspassword")} softname=nonempty:${nonEmpty("softname")}")
                }
                // Which parameters a media download really needs (status, type and size only).
                val http = okhttp3.OkHttpClient.Builder().callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
                fun probe(label: String, u: okhttp3.HttpUrl) {
                    val res = runCatching {
                        http.newCall(okhttp3.Request.Builder().url(u).get().build()).execute().use { r ->
                            var n = 0L
                            r.body?.byteStream()?.use { st -> val buf = ByteArray(8192); while (n < 4_000_000) { val k = st.read(buf); if (k < 0) break; n += k } }
                            val head = r.body?.contentType()?.toString() ?: "-"
                            "http=${r.code} type=$head bytes>=$n"
                        }
                    }.getOrElse { "error=${it::class.java.simpleName}" }
                    line("media_probe $label $res")
                }
                val medias = runCatching {
                    com.google.gson.JsonParser.parseString(body).asJsonObject.getAsJsonObject("response").getAsJsonObject("jeu").getAsJsonArray("medias")
                        .mapNotNull { it as? com.google.gson.JsonObject }
                }.getOrDefault(emptyList())
                fun pickUrl(vararg types: String): okhttp3.HttpUrl? = types.firstNotNullOfOrNull { t ->
                    medias.firstOrNull { it.get("type")?.asString == t }?.get("url")?.asString?.toHttpUrlOrNull()
                }
                for ((what, u0) in listOf("box-2D" to pickUrl("box-2D"), "manuel" to pickUrl("manuel"), "video" to pickUrl("video-normalized", "video"))) {
                    val u = u0 ?: run { line("media_probe $what none"); continue }
                    fun without(vararg names: String) = u.newBuilder().apply { names.forEach { removeAllQueryParameters(it) } }.build()
                    line("media_probe $what path=${u.encodedPath.substringAfterLast('/')}")
                    probe("$what as_returned", u)
                    probe("$what no_devpassword", without("devpassword"))
                    probe("$what no_dev_key(devid,devpassword)", without("devid", "devpassword"))
                    probe("$what no_dev_key_no_softname", without("devid", "devpassword", "softname"))
                    probe("$what no_credentials_at_all", without("devid", "devpassword", "softname", "ssid", "sspassword"))
                }
                val types = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toSet()
                line("media_types_present manuel=${"manuel" in types} video=${"video" in types || "video-normalized" in types} box-2D=${"box-2D" in types}")
            }

            // ---- 2. the app's own look-ups ----
            val game = if (c != null || (svc.configured && creds != null && !creds.username.isNullOrBlank())) TestHooks.matchGame(games(), query) else null
            line("== 2. game=${game?.name ?: "skipped / NO MATCH"} platform=${game?.platform}")
            if (game != null) {
                val k = game.stableKey
                vm.scrapeMetadataFor(game, MetadataSource.SCREENSCRAPER, game.name)
                vm.loadPhysicalMedia(game, force = true)
                vm.loadVideoUrl(game, force = true)
                var manualFile: File? = null; var manualMissing = false
                vm.openManual(game, { manualFile = it }, { manualMissing = true })
                for (i in 0 until 60) { delay(1000); if (art()[k]?.gridUrl != null && (manualFile != null || manualMissing)) break }
                delay(3000)
                val cover = art()[k]?.gridUrl
                line("art_grid_set=${cover != null} manual_file=${manualFile != null} manual_missing=$manualMissing")
                // A cover picked from ScreenScraper's alternatives (what Artwork & details › pick does): art_overrides, which Drive sync carries.
                if (cover != null) vm.setArtOverride(game, com.shelf.launcher.data.ArtSlot.COVER, cover)
                delay(1500)
                line("art_grid ${fmt(needles.count(cover.orEmpty()))}")
                // ---- 2b. downloads through the app's own clients (key added only on the way out) ----
                withContext(Dispatchers.IO) {
                    fun get(label: String, url: String?, range: Boolean) {
                        if (url == null) { line("download $label none"); return }
                        val client = okhttp3.OkHttpClient.Builder().addInterceptor(com.shelf.launcher.data.SsMedia.interceptor)
                            .addInterceptor(com.shelf.launcher.data.VoltcapService.interceptor).build()
                        val rq = okhttp3.Request.Builder().url(url).apply { if (range) header("Range", "bytes=0-65535") }.build()
                        val res = runCatching { client.newCall(rq).execute().use { r ->
                            var n = 0L; r.body?.byteStream()?.use { st -> val b = ByteArray(8192); while (n < 3_000_000) { val k = st.read(b); if (k < 0) break; n += k } }
                            "http=${r.code} type=${r.body?.contentType()} bytes>=$n" } }.getOrElse { "error=${it::class.java.simpleName}" }
                        line("download $label stored_has_credentials=${com.shelf.launcher.data.SsMedia.hasCredentials(url)} $res")
                    }
                    get("cover(art_cache)", cover, false)
                    val phys = app.getSharedPreferences("physical_media", Context.MODE_PRIVATE).getString(k, null)
                    get("scan(physical_media)", phys?.lineSequence()?.mapNotNull { it.substringAfter('\t', "").takeIf { u -> u.contains("screenscraper") } }?.firstOrNull(), false)
                    get("manual(manual_urls)", com.shelf.launcher.data.ManualLinks.url(k), false)
                    get("clip(video_urls, range)", app.getSharedPreferences("video_urls", Context.MODE_PRIVATE).getString(k, null)?.takeIf { it.isNotEmpty() }, true)
                    // Coil, as the screens load pictures.
                    val img = runCatching {
                        val r = coil.Coil.imageLoader(app).execute(coil.request.ImageRequest.Builder(app).data(cover).memoryCachePolicy(coil.request.CachePolicy.DISABLED).build())
                        if (r is coil.request.SuccessResult) "ok ${r.drawable.intrinsicWidth}x${r.drawable.intrinsicHeight}" else "failed"
                    }.getOrElse { "error=${it::class.java.simpleName}" }
                    line("coil cover $img")
                }
            }

            // ---- 3. every file ----
            withContext(Dispatchers.IO) {
                line("== 3. files (path relative to the app folder; counts only)")
                val roots = listOfNotNull(
                    "data" to File(app.applicationInfo.dataDir),
                    app.getExternalFilesDir(null)?.parentFile?.let { "ext" to it }
                )
                var scanned = 0
                for ((tag, root) in roots) {
                    root.walkTopDown().filter { it.isFile && it.length() in 1..60_000_000 && it.name != report.name }.forEach { f ->
                        scanned++
                        val text = runCatching { String(f.readBytes(), Charsets.ISO_8859_1) }.getOrNull() ?: return@forEach
                        val m = needles.count(text)
                        if (m.values.any { it > 0 }) line("$tag/${f.relativeTo(root).path} ${fmt(m)}")
                    }
                }
                line("files_scanned=$scanned")
            }

            // ---- 4. backups, built locally and deleted ----
            withContext(Dispatchers.IO) {
                line("== 4. backups (never uploaded)")
                val B = com.shelf.launcher.data.Backup
                line("exportText(drive/nearby, device setup, no accounts) ${fmt(needles.count(B.exportText(app, "qa517", includeDeviceSetup = true, includeAccounts = false)))}")
                line("exportText(drive/nearby, accounts) ${fmt(needles.count(B.exportText(app, "qa517", includeDeviceSetup = true, includeAccounts = true)))}")
                for ((label, gd, acc) in listOf(Triple("export(no game data)", false, false), Triple("export(game data)", true, false), Triple("export(game data, accounts)", true, true))) {
                    val tmp = File(app.cacheDir, "qa517_${System.nanoTime()}.json")
                    runCatching { B.exportTo(app, tmp, includeGameData = gd, includeDeviceSetup = true, includeAccounts = acc) }
                        .onFailure { line("$label FAILED ${it::class.java.simpleName}") }
                    line("$label ${fmt(needles.count(runCatching { tmp.readText() }.getOrDefault("")))}")
                    tmp.delete()
                }
                val ov = app.getSharedPreferences("art_overrides", Context.MODE_PRIVATE).getString("overrides_json", null).orEmpty()
                line("art_overrides (Drive sync payload source) ${fmt(needles.count(ov))}")
            }
            // ---- 5. saved pictures / manuals still found under the stored (clean) addresses ----
            withContext(Dispatchers.IO) {
                com.shelf.launcher.data.ArtFiles.init(app)
                val flags = app.getSharedPreferences("migrations", Context.MODE_PRIVATE)
                line("== 5. migration ss_url_hygiene=${flags.getInt("ss_url_hygiene", 0)} values_changed=${flags.getInt("ss_url_hygiene_values", -1)}")
                val urlRe = Regex("""https?://[^\s"'<>\\]+""")
                for (name in listOf("art_cache", "physical_media", "screenshots_v2", "manual_urls", "art_overrides", "video_urls")) {
                    val urls = app.getSharedPreferences(name, Context.MODE_PRIVATE).all.values.filterIsInstance<String>()
                        .flatMap { v -> urlRe.findAll(v.replace("\\u003d", "=").replace("\\u0026", "&").replace("\\/", "/")).map { it.value } }
                        .filter { it.contains("screenscraper", true) }.toSet()
                    val saved = urls.count { com.shelf.launcher.data.ArtFiles.has(it) || com.shelf.launcher.data.ArtFiles.manualFile(it) != null }
                    line("$name ss_addresses=${urls.size} signed=${urls.count { "vsig=" in it }} saved_file_found=$saved still_with_credentials=${urls.count { com.shelf.launcher.data.SsMedia.hasCredentials(it) }}")
                }
            }
            if (usedKeyFile) withContext(Dispatchers.IO) { store.clear(); line("qa_key_cleared=${store.load() == null}") }
            line("DONE")
        } catch (e: Throwable) {
            line("ERROR ${e::class.java.simpleName}")
        } finally {
            runCatching { report.writeText(out.toString()) }
        }
    }
}
