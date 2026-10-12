package com.shelf.launcher.data

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Google hasn't granted Drive access (yet), or took it back: the player has to
 * press "Connect Google Drive" again. */
open class DriveSignInNeeded(message: String = "Google Drive needs to be connected again.") : Exception(message)

/**
 * Google refused this *copy of the app* (Play Services error 10, DEVELOPER_ERROR): the APK's
 * signing key (SHA-1) or package name isn't registered as an Android OAuth client in the Google
 * Cloud project. Signing in again can't fix it, so the player is told what's actually wrong.
 */
class DriveNotRegistered : DriveSignInNeeded(DriveErrors.NOT_REGISTERED)

/** Something went wrong that is not about consent (network, Play Services hiccup): try again later. */
open class DriveRetryable(message: String, cause: Throwable? = null) : Exception(message, cause)

/** No usable connection to Google right now (offline, timeout, airplane mode). Never means "sign in again". */
class DriveOffline(cause: Throwable? = null) : DriveRetryable("Couldn't reach Google — check the connection.", cause)

/** Plain-words explanations for what Google answers when connecting Drive fails. */
object DriveErrors {
    const val NOT_REGISTERED =
        "This copy of Voltcap isn't registered with Google (error 10). The key it was signed with " +
            "isn't added to Voltcap's Google Cloud project, so Google won't hand out Drive access — " +
            "signing in again can't fix it. Install the official build, or add this build's SHA-1 " +
            "(package com.shelf.launcher) as an Android OAuth client."

    const val NOT_COMPLETED =
        "Google Drive wasn't connected. If Google showed \"Access blocked\" or \"hasn't completed " +
            "the Google verification process\", Voltcap's Google Cloud project is still in Testing and " +
            "only its listed test users can connect."

    /** The Play Services status code inside [t], if there is one. */
    fun statusCode(t: Throwable?): Int? {
        var e = t
        while (e != null) {
            if (e is com.google.android.gms.common.api.ApiException) return e.statusCode
            e = e.cause?.takeIf { it !== e }
        }
        return null
    }

    /** What to tell the player when asking Google for Drive access threw [t]. */
    fun explain(t: Throwable): String = when (statusCode(t)) {
        10 -> NOT_REGISTERED
        CommonStatusCodes.CANCELED, 12501 -> NOT_COMPLETED
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> "Couldn't reach Google — check the connection and try again."
        CommonStatusCodes.API_NOT_CONNECTED, CommonStatusCodes.SERVICE_DISABLED, CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED ->
            "Google Play services is missing, switched off or too old on this device — Drive sync needs it."
        12500 -> "Google couldn't finish the sign-in (error 12500). Update Google Play services and try again; " +
            "if it keeps happening, the app's Google Cloud setup (consent screen) is incomplete."
        null -> when (t) {
            is java.util.concurrent.TimeoutException, is java.io.IOException -> "Couldn't reach Google — check the connection and try again."
            else -> "Couldn't connect Google Drive: ${t.message ?: t.javaClass.simpleName}"
        }
        else -> "Couldn't connect Google Drive (Google error ${statusCode(t)}): ${t.message ?: ""}".trimEnd(' ', ':')
    }
}

/**
 * Google sign-in for Drive through Play Services' AuthorizationClient: no
 * passwords or refresh tokens stored by Voltcap — Play Services hands out a
 * fresh access token whenever asked, silently once the player has agreed.
 *
 * The scope is drive.file: Voltcap only ever sees the files and folders it
 * created itself (the "Voltcap Sync" folder), nothing else in the Drive.
 */
object DriveAuth {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    @Volatile private var cached: String? = null
    @Volatile private var cachedAt = 0L

    /** Blocking — call from a background thread. */
    fun token(context: Context, forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        val old = cached
        if (!forceRefresh && old != null && now - cachedAt < 45 * 60_000L) return old
        if (forceRefresh && old != null) {
            // Makes Play Services mint a new token instead of handing the dead one back.
            runCatching { GoogleAuthUtil.clearToken(context, old) }
            cached = null
        }
        val result: AuthorizationResult = try {
            Tasks.await(Identity.getAuthorizationClient(context).authorize(request()), 30, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            throw DriveOffline(e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw DriveRetryable("Interrupted while signing in to Google.", e)
        } catch (e: java.util.concurrent.ExecutionException) {
            throw classify(context, e.cause ?: e)
        } catch (e: Exception) {
            throw classify(context, e)
        }
        if (result.hasResolution()) {
            setNeedsSignIn(context, true)
            throw DriveSignInNeeded()
        }
        val token = result.accessToken
        if (token == null) {
            setNeedsSignIn(context, true)
            throw DriveSignInNeeded()
        }
        cached = token
        cachedAt = now
        setNeedsSignIn(context, false)
        return token
    }

    /**
     * Sorts an authorize failure: genuinely needs the player (consent, revoked
     * access) vs. the network/Play Services having a bad moment. Only the first
     * kind may ever say "connect again".
     */
    private fun classify(context: Context, cause: Throwable): Exception {
        var t: Throwable? = cause
        while (t != null) {
            when (t) {
                is com.google.android.gms.common.api.ApiException -> return when (t.statusCode) {
                    10 /* DEVELOPER_ERROR: this build's signing key isn't registered */ -> {
                        setNeedsSignIn(context, true)
                        setProblem(context, DriveErrors.NOT_REGISTERED)
                        DriveNotRegistered()
                    }
                    CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.RESOLUTION_REQUIRED,
                    CommonStatusCodes.CANCELED, 12500, 12501 -> {
                        setNeedsSignIn(context, true)
                        DriveSignInNeeded()
                    }
                    CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERRUPTED,
                    CommonStatusCodes.RECONNECTION_TIMED_OUT, CommonStatusCodes.RECONNECTION_TIMED_OUT_DURING_UPDATE,
                    CommonStatusCodes.API_NOT_CONNECTED -> DriveOffline(t)
                    else -> DriveRetryable("Google sign-in failed (code ${t.statusCode}).", t)
                }
                is java.io.IOException -> return DriveOffline(t)
            }
            t = t.cause?.takeIf { it !== t }
        }
        return DriveRetryable("Google sign-in failed: ${cause.message ?: cause.javaClass.simpleName}", cause)
    }

    // The "needs connecting again" flag is a plain yes/no in cloud_sync prefs (no secrets), so the
    // notice survives a restart and is cleared by the next successful authorisation.
    @Volatile private var needsSignInCache: Boolean? = null

    fun needsSignIn(context: Context): Boolean =
        needsSignInCache ?: runCatching {
            context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE).getBoolean("needs_sign_in", false)
        }.getOrDefault(false).also { needsSignInCache = it }

    fun setNeedsSignIn(context: Context, value: Boolean) {
        if (!value) setProblem(context, null)
        if (needsSignInCache == value) return
        needsSignInCache = value
        runCatching { context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE).edit().putBoolean("needs_sign_in", value).apply() }
    }

    // Why Google refused, when signing in again can't help (e.g. error 10). Shown instead of
    // "confirm access again" so the player isn't sent round in circles.
    @Volatile private var problemCache: String? = null
    @Volatile private var problemLoaded = false

    fun problem(context: Context): String? {
        if (!problemLoaded) {
            problemCache = runCatching {
                context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE).getString("auth_problem", null)
            }.getOrNull()
            problemLoaded = true
        }
        return problemCache
    }

    fun setProblem(context: Context, text: String?) {
        if (problemLoaded && problemCache == text) return
        problemCache = text
        problemLoaded = true
        runCatching {
            context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE).edit().apply {
                if (text == null) remove("auth_problem") else putString("auth_problem", text)
            }.apply()
        }
    }

    fun forget() {
        cached = null
        cachedAt = 0L
    }
}

/** One file or folder in Drive, the fields Voltcap asks for. */
data class DriveFile(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val md5: String?,
    val modifiedTime: Long,
    val size: Long,
    /** The device that uploaded this version (set by Voltcap on upload). */
    val device: String? = null
)

/**
 * The few Drive v3 REST calls sync needs, over the OkHttp the app already
 * ships with (the Google API client library would add megabytes for this).
 * Every call blocks: run it on Dispatchers.IO.
 */
class DriveClient(private val context: Context) {
    // One client (connection pool + threads) for every DriveClient: sync makes a new DriveClient per run.
    private val http get() = sharedHttp

    // Folder IDs are shared by every client in the app's lifetime: looking
    // them up (three requests) on each sync made "checking for newer saves"
    // before a game noticeably slower.
    private val folderCache get() = sharedFolderIds

    /** Runs the request with a token; a 401 gets one retry with a new token. */
    private fun execute(build: () -> Request.Builder): Response {
        var response = call(build().header("Authorization", "Bearer ${DriveAuth.token(context)}").build())
        if (response.code == 401) {
            response.close()
            val fresh = DriveAuth.token(context, forceRefresh = true)
            response = call(build().header("Authorization", "Bearer $fresh").build())
        }
        if (response.code == 401 || response.code == 403 && response.peekBody(4096).string().contains("insufficient", true)) {
            response.close()
            DriveAuth.setNeedsSignIn(context, true)
            throw DriveSignInNeeded()
        }
        if (!response.isSuccessful) {
            val body = runCatching { response.body?.string()?.take(300) }.getOrNull()
            response.close()
            error("Drive answered ${response.code}${body?.let { ": $it" } ?: ""}")
        }
        return response
    }

    /**
     * One request. A dropped connection (no network, timeout) is [DriveOffline], so a sync stops at once and is
     * retried later instead of failing file after file (update 91).
     */
    private fun call(request: Request): Response = try {
        http.newCall(request).execute()
    } catch (e: java.io.IOException) {
        throw DriveOffline(e)
    }

    private fun json(build: () -> Request.Builder): JSONObject =
        execute(build).use { JSONObject(it.body?.string() ?: "{}") }

    /** All files (not in the bin) directly inside [parentId], every page. */
    fun list(parentId: String, extraQuery: String? = null): List<DriveFile> {
        val out = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val q = buildString {
                append("'${escape(parentId)}' in parents and trashed=false")
                if (extraQuery != null) append(" and ").append(extraQuery)
            }
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("orderBy", "createdTime")
                .addQueryParameter("fields", "nextPageToken,files($FIELDS)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = json { Request.Builder().url(url).get() }
            val files = page.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) out += parse(files.getJSONObject(i))
            pageToken = page.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return out
    }

    /** The folder called [name] in [parentId] ("root" = top of My Drive), made if missing. */
    fun folder(name: String, parentId: String = "root"): String {
        val cacheKey = "$parentId/$name"
        folderCache[cacheKey]?.let { return it }
        val existing = list(parentId, "name='${escape(name)}' and mimeType='$FOLDER'").firstOrNull { it.isFolder }
        val id = existing?.id ?: run {
            val body = JSONObject()
                .put("name", name)
                .put("mimeType", FOLDER)
                .put("parents", JSONArray().put(parentId))
                .toString().toRequestBody(JSON_TYPE)
            json { Request.Builder().url("$API/files?fields=id").post(body) }.getString("id")
        }
        folderCache[cacheKey] = id
        return id
    }

    /**
     * Uploads [content] as [name] in [parentId] — or as a new version of
     * [existingId] — through a resumable session, so big memory cards and
     * save states work the same as tiny .srm files.
     */
    fun upload(content: RequestBody, name: String, parentId: String, existingId: String?, modifiedTime: Long): DriveFile {
        val meta = JSONObject()
            .put("modifiedTime", formatTime(modifiedTime))
            .put("appProperties", JSONObject().put("device", android.os.Build.MODEL).put("install", installId(context)))
        if (existingId == null) meta.put("name", name).put("parents", JSONArray().put(parentId))
        val start = if (existingId == null) "$UPLOAD/files?uploadType=resumable&fields=$FIELDS"
        else "$UPLOAD/files/$existingId?uploadType=resumable&fields=$FIELDS"
        val metaBody = meta.toString().toRequestBody(JSON_TYPE)
        val session = execute {
            Request.Builder().url(start).apply {
                if (existingId == null) post(metaBody) else patch(metaBody)
            }
        }.use { it.header("Location") } ?: error("Drive didn't start the upload.")
        // The session URL carries its own authorisation.
        return call(Request.Builder().url(session).put(content).build()).use { response ->
            if (!response.isSuccessful) error("Upload failed (${response.code}).")
            parse(JSONObject(response.body?.string() ?: "{}"))
        }
    }

    fun uploadFile(file: File, name: String, parentId: String, existingId: String?): DriveFile =
        upload(file.asRequestBody(BINARY), name, parentId, existingId, file.lastModified())

    fun uploadBytes(bytes: ByteArray, name: String, parentId: String, existingId: String?): DriveFile =
        upload(bytes.toRequestBody(JSON_TYPE), name, parentId, existingId, System.currentTimeMillis())

    /** Downloads [id] into [target] via a temporary file next to it, so a
     * dropped connection never leaves half a save behind. */
    fun download(id: String, target: File, modifiedTime: Long? = null) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, ".${target.name}.voltcap-part")
        execute { Request.Builder().url("$API/files/$id?alt=media").get() }.use { response ->
            val body = response.body ?: error("Empty download.")
            part.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
        }
        // A rename replaces the old save in one step; deleting it first would leave no save at all if
        // Voltcap died in between. Only where the rename can't replace does the old file go first.
        if (!part.renameTo(target)) {
            if (target.exists() && !target.delete()) {
                part.delete()
                error("Couldn't replace ${target.name}.")
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
        }
        modifiedTime?.let { target.setLastModified(it) }
    }

    /** To Drive's bin (recoverable for 30 days), not deleted outright. */
    fun trash(id: String) {
        val body = JSONObject().put("trashed", true).toString().toRequestBody(JSON_TYPE)
        execute { Request.Builder().url("$API/files/$id?fields=id").patch(body) }.close()
    }

    fun downloadBytes(id: String): ByteArray =
        execute { Request.Builder().url("$API/files/$id?alt=media").get() }.use { it.body?.bytes() ?: ByteArray(0) }

    /** Update 110: like [downloadBytes], but an empty array (nothing read) when Drive says the file is bigger than [maxBytes]. */
    fun downloadBytes(id: String, maxBytes: Long): ByteArray =
        execute { Request.Builder().url("$API/files/$id?alt=media").get() }.use { r ->
            val body = r.body ?: return@use ByteArray(0)
            if (body.contentLength() > maxBytes) ByteArray(0) else body.bytes()
        }

    /** Where Drive's change feed starts from now. */
    fun startPageToken(): String =
        json { Request.Builder().url("$API/changes/startPageToken").get() }.getString("startPageToken")

    /** One file that changed in Drive since [token]. [install]: which Voltcap uploaded it. */
    data class Change(val fileId: String, val removed: Boolean, val install: String?)

    /**
     * What changed in Drive since [token] (only files Voltcap made — that's all
     * drive.file can see). Returns the changes and the token for next time.
     */
    fun changes(token: String): Pair<List<Change>, String> {
        val out = mutableListOf<Change>()
        var page: String? = token
        var next = token
        while (page != null) {
            val url = "$API/changes".toHttpUrl().newBuilder()
                .addQueryParameter("pageToken", page)
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("pageSize", "200")
                .addQueryParameter("fields", "nextPageToken,newStartPageToken,changes(fileId,removed,file(appProperties))")
                .build()
            val body = json { Request.Builder().url(url).get() }
            val list = body.optJSONArray("changes") ?: JSONArray()
            for (i in 0 until list.length()) {
                val c = list.getJSONObject(i)
                out += Change(
                    fileId = c.optString("fileId"),
                    removed = c.optBoolean("removed"),
                    install = c.optJSONObject("file")?.optJSONObject("appProperties")?.optString("install")?.takeIf { it.isNotEmpty() }
                )
            }
            page = body.optString("nextPageToken").takeIf { it.isNotEmpty() }
            body.optString("newStartPageToken").takeIf { it.isNotEmpty() }?.let { next = it }
        }
        return out to next
    }

    private fun parse(o: JSONObject) = DriveFile(
        id = o.optString("id"),
        name = o.optString("name"),
        isFolder = o.optString("mimeType") == FOLDER,
        md5 = o.optString("md5Checksum").takeIf { it.isNotEmpty() },
        modifiedTime = parseTime(o.optString("modifiedTime")),
        size = o.optString("size").toLongOrNull() ?: 0L,
        device = o.optJSONObject("appProperties")?.optString("device")?.takeIf { it.isNotEmpty() }
    )

    companion object {
        private val sharedFolderIds = java.util.concurrent.ConcurrentHashMap<String, String>()

        private val sharedHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build()
        }

        /** A random id for this Voltcap install, so its own uploads can be told
         * apart from the other handheld's — even when both are the same model. */
        fun installId(context: Context): String {
            val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
            val fingerprint = DeviceIdentity.fingerprint(context)
            val saved = prefs.getString("install_id", null)
            // An id that arrived with a restored backup belongs to the other handheld: using it would
            // make both devices think their own uploads came from "here". A new one costs one extra sync.
            if (saved != null && prefs.getString("install_fp", null) == fingerprint) return saved
            val id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("install_id", id).putString("install_fp", fingerprint).remove("changes_token").apply()
            return id
        }

        /** After a folder went missing in Drive (binned by hand…), look them up again. */
        fun forgetFolders() = sharedFolderIds.clear()

        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER = "application/vnd.google-apps.folder"
        private const val FIELDS = "id,name,mimeType,md5Checksum,modifiedTime,size,appProperties"
        private val JSON_TYPE = "application/json; charset=UTF-8".toMediaType()
        private val BINARY = "application/octet-stream".toMediaType()

        private fun escape(s: String) = s.replace("\\", "\\\\").replace("'", "\\'")

        // java.time needs API 26; minSdk is 24.
        private fun format() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        fun formatTime(ms: Long): String = format().format(java.util.Date(ms))

        fun parseTime(s: String): Long {
            if (s.isEmpty()) return 0L
            return runCatching { format().parse(s)!!.time }.getOrElse {
                runCatching {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)!!.time
                }.getOrDefault(0L)
            }
        }
    }
}
