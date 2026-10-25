package com.shelf.launcher.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed class LaunchOutcome {
    object Launched : LaunchOutcome()
    object NotConfigured : LaunchOutcome()
    data class EmulatorNotFound(val packageName: String?) : LaunchOutcome()
    data class Error(val message: String) : LaunchOutcome()
}

/** Result of opening a plain app; [message] is user-facing. */
sealed class AppLaunchResult {
    object Launched : AppLaunchResult()
    data class Failed(val message: String) : AppLaunchResult()
}

object GameLauncher {

    /**
     * Opens an installed app straight away — no companion screen, no delays,
     * no play session. [displayId] is passed to Android explicitly (null only
     * when the device has a single screen).
     *
     * Android limitation: with NEW_TASK alone, an app that already has a task
     * on another display is simply brought forward there and the requested
     * display is ignored. [newInstance] adds MULTIPLE_TASK so a fresh task is
     * created on the requested display instead; the caller only sets it when
     * that's safe (not for emulators/games, where two instances would fight
     * over save files). Apps declaring singleTask/singleInstance ignore it.
     */
    fun launchAppOn(
        context: Context,
        packageName: String,
        displayId: Int?,
        label: String? = null,
        newInstance: Boolean = false
    ): AppLaunchResult {
        val name = label ?: packageName
        val pm = context.packageManager
        val enabled = try {
            pm.getApplicationInfo(packageName, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            return AppLaunchResult.Failed("$name isn't installed any more.")
        } catch (e: Exception) {
            true
        }
        if (!enabled) return AppLaunchResult.Failed("$name is disabled — enable it in Android settings first.")
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?: return AppLaunchResult.Failed("$name has no screen Voltcap can open.")
        if (displayId != null) {
            val display = context.getSystemService(android.hardware.display.DisplayManager::class.java)?.getDisplay(displayId)
            if (display == null || !display.isValid) {
                return AppLaunchResult.Failed("That screen isn't connected any more, so $name wasn't opened.")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (newInstance && displayId != null) intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        return try {
            context.startActivity(intent, ScreenPlacement.optionsFor(displayId))
            AppLaunchResult.Launched
        } catch (e: ActivityNotFoundException) {
            AppLaunchResult.Failed("$name has no screen Voltcap can open.")
        } catch (e: SecurityException) {
            AppLaunchResult.Failed("Android didn't let $name open on that screen.")
        } catch (e: Exception) {
            AppLaunchResult.Failed("Couldn't open $name: ${e.message ?: "unknown error"}")
        }
    }

    fun launch(
        context: Context,
        game: Game,
        config: EmulatorConfig?,
        gameNativeConfig: GameNativeConfig,
        /** The scanned ROM folder — storage links are rooted there. */
        romRoot: String? = null,
        /** Which screen the game goes on (Screen layouts); null = Android decides. */
        displayId: Int? = null,
        /** Per-game launch parameters (already validated); only added to ROM launches. */
        extras: List<LaunchExtra> = emptyList()
    ): LaunchOutcome {
        val options = ScreenPlacement.optionsFor(displayId)
        return when (game.sourceType) {
            SourceType.APP -> launchApp(context, game, options)
            SourceType.ROM -> launchRom(context, game, config, romRoot, options, extras)
            SourceType.STEAM -> launchSteamGame(context, game, gameNativeConfig, options)
        }
    }

    private fun launchSteamGame(context: Context, game: Game, config: GameNativeConfig, options: android.os.Bundle?): LaunchOutcome {
        val appId = game.steamAppId ?: return LaunchOutcome.Error("Steam entry has no App ID")
        val intent = Intent(config.action).apply {
            setClassName(config.packageName, config.activityClassName)
            putExtra(config.appIdExtraKey, appId) // must stay an Int extra — GameNative's
                                                  // documented intent uses -ei (integer), not -e (string)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent, options)
            LaunchOutcome.Launched
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome.EmulatorNotFound(config.packageName)
        } catch (e: Exception) {
            LaunchOutcome.Error(e.message ?: "Unknown error launching GameNative")
        }
    }

    private fun launchApp(context: Context, game: Game, options: android.os.Bundle?): LaunchOutcome {
        val packageName = game.packageName ?: return LaunchOutcome.Error("App entry has no package name")
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchOutcome.EmulatorNotFound(packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent, options)
            LaunchOutcome.Launched
        } catch (e: ActivityNotFoundException) {
            LaunchOutcome.EmulatorNotFound(packageName)
        } catch (e: Exception) {
            LaunchOutcome.Error(e.message ?: "Unknown error launching $packageName")
        }
    }

    private fun launchRom(context: Context, game: Game, config: EmulatorConfig?, romRoot: String?, options: android.os.Bundle?, extras: List<LaunchExtra> = emptyList()): LaunchOutcome {
        if (game.romPath?.endsWith(".droiddeck", ignoreCase = true) == true) return GameClients.launchDroidDeck(context, game.romPath, options)
        // A home-screen shortcut another app added (DroidDeck's "Add to home screen"): PinnedShortcuts.
        if (PinnedShortcuts.isShortcut(game)) return PinnedShortcuts.launch(context, game, options)
        if (config == null) return LaunchOutcome.NotConfigured
        val romPath = game.romPath ?: return LaunchOutcome.Error("ROM entry has no file path")

        // Tried in order; the first one Android can start wins. Later entries
        // are fallbacks for forks or renamed builds, so a game still opens in
        // the right emulator instead of failing with "not found".
        val attempts = when (config.mode) {
            LaunchMode.SYSTEM_CHOOSER -> listOf(viewIntent(context, romPath, null, RomFormat.SHARED_URI, romRoot))
            LaunchMode.EXPLICIT_INTENT -> explicitAttempts(context, romPath, config, romRoot)
        }

        var lastError: Exception? = null
        for (intent in attempts) {
            // Application context can't start activities without this flag.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (extras.isNotEmpty()) LaunchExtras.apply(intent, extras)
            if (intent.data?.scheme == ContentResolver.SCHEME_FILE) allowFileUriIntents()
            try {
                context.startActivity(intent, options)
                return LaunchOutcome.Launched
            } catch (e: ActivityNotFoundException) {
                lastError = e
            } catch (e: SecurityException) {
                lastError = e
            } catch (e: Exception) {
                return LaunchOutcome.Error(e.message ?: "Unknown error launching emulator")
            }
        }
        return if (lastError is SecurityException) {
            LaunchOutcome.Error(lastError.message ?: "The emulator refused the launch")
        } else {
            LaunchOutcome.EmulatorNotFound(config.packageName)
        }
    }

    private fun explicitAttempts(context: Context, romPath: String, config: EmulatorConfig, shelfRomRoot: String?): List<Intent> {
        // Storage links must be rooted at the folder the emulator was given.
        var romRoot = config.accessFolder ?: shelfRomRoot
        val pkg = config.packageName ?: return listOf(viewIntent(context, romPath, null, RomFormat.SHARED_URI, romRoot))

        if (CoreRegistry.isRetroArch(pkg)) return listOf(retroArchIntent(context, pkg, romPath, config))

        val profile = LaunchProfiles.find(pkg)
        // (Update 326: Eden's family — rooted at the game's own folder unless a folder is set for the emulator.)
        if (config.accessFolder == null && profile?.treeAtGameFolder == true) File(romPath).parent?.let { romRoot = it }
        val chosen = config.romFormat?.takeIf { it != RomFormat.AUTO }
        val attempts = mutableListOf<Intent>()

        if (config.activityClassName != null) {
            // Set up by hand (advanced setup): use exactly what was entered.
            attempts += Intent(Intent.ACTION_VIEW).apply {
                setClassName(pkg, config.activityClassName)
                val format = chosen ?: RomFormat.SHARED_URI
                applyRom(this, context, pkg, romPath, config.romExtraKey, format, romRoot)
                if (config.coreExtraKey != null && config.coreExtraValue != null) {
                    putExtra(config.coreExtraKey, config.coreExtraValue)
                }
            }
        } else if (profile != null) {
            val format = chosen ?: profile.format
            // Update 175: a game that is a whole folder (a PS3 game folder): the emulator's folder extra with the plain
            // path (aPS3e's "game_dir", as ES-DE launches it) — a folder can't be shared as a file link.
            val dirExtra = if (File(romPath).isDirectory) FOLDER_GAME_EXTRAS[pkg] else null
            fun build(activity: String?) = Intent(profile.action).apply {
                if (activity != null) setClassName(pkg, activity) else setPackage(pkg)
                if (profile.action == Intent.ACTION_VIEW) addCategory(Intent.CATEGORY_DEFAULT)
                if (dirExtra != null) putExtra(dirExtra, romPath)
                else applyRom(this, context, pkg, romPath, profile.romExtra, format, romRoot, profile.mimeType)
                if (profile.alsoAsData && profile.romExtra != null) {
                    applyRom(this, context, pkg, romPath, null, format, romRoot, profile.mimeType)
                }
                profile.boolExtras.forEach { (key, value) -> putExtra(key, value) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            // Only activities that really exist in the installed build — a wrong
            // guess used to fall through to the emulator's home screen, which
            // "works" (the app opens) but never boots the game.
            val activities = (profile.activities.filter { activityExists(context, pkg, it) } +
                discoverEmulationActivities(context, pkg)).distinct()
            activities.forEach { attempts += build(it) }
            // Let Android pick the emulator activity that accepts this kind of file.
            if (profile.action == Intent.ACTION_VIEW) attempts += build(null)
        }

        // Generic fallback: "open this file" addressed to the emulator.
        attempts += viewIntent(context, romPath, pkg, chosen ?: RomFormat.SHARED_URI, romRoot)
        return attempts
    }

    /** Update 175: emulators that boot a game folder, and the extra that takes its path. */
    private val FOLDER_GAME_EXTRAS = mapOf("aenu.aps3e" to "game_dir")

    private fun activityExists(context: Context, pkg: String, activity: String): Boolean = runCatching {
        context.packageManager.getActivityInfo(android.content.ComponentName(pkg, activity), 0)
        true
    }.getOrDefault(false)

    /** Exported activities named like an emulation screen, for builds whose
     * class names moved. */
    private fun discoverEmulationActivities(context: Context, pkg: String): List<String> = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
        info.activities.orEmpty()
            .filter { it.exported }
            .map { it.name }
            .filter { name ->
                val simple = name.substringAfterLast('.')
                simple.equals("EmulationActivity", true) || simple.equals("EmulatorActivity", true)
            }
    }.getOrDefault(emptyList())

    /**
     * RetroArch's automation entry point, with the same extras RetroArch's own
     * launcher passes to it (ROM, core, config, data dirs, keyboard). Leaving
     * out the data folders is what made RetroArch hang with "isn't
     * responding" instead of booting the game.
     */
    private fun retroArchIntent(context: Context, pkg: String, romPath: String, config: EmulatorConfig): Intent {
        val appInfo = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val sdcard = Environment.getExternalStorageDirectory().absolutePath
        val external = "$sdcard/Android/data/$pkg/files"
        return Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkg, config.activityClassName ?: CoreRegistry.RETROARCH_ACTIVITY)
            putExtra(config.romExtraKey ?: CoreRegistry.ROM_EXTRA_KEY, romPath)
            config.coreExtraValue?.let { putExtra(config.coreExtraKey ?: CoreRegistry.CORE_EXTRA_KEY, CoreRegistry.fixCorePath(it)) }
            putExtra("CONFIGFILE", "$external/retroarch.cfg")
            putExtra("DATADIR", appInfo?.dataDir ?: "/data/data/$pkg")
            appInfo?.sourceDir?.let { putExtra("APK", it) }
            putExtra("SDCARD", sdcard)
            putExtra("EXTERNAL", external)
            putExtra("DOWNLOADS", "$sdcard/Download")
            putExtra("SCREENSHOTS", "$sdcard/Pictures")
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let { putExtra("IME", it) }
            // Replaces a RetroArch session that's already running.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    private fun viewIntent(context: Context, romPath: String, pkg: String?, format: RomFormat, romRoot: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            if (pkg != null) setPackage(pkg)
            applyRom(this, context, pkg, romPath, null, format, romRoot)
        }

    /** Puts the game on the intent — as the named extra, or as its data — in
     * the requested format. */
    private fun applyRom(
        intent: Intent,
        context: Context,
        targetPackage: String?,
        romPath: String,
        extraKey: String?,
        format: RomFormat,
        romRoot: String?,
        mimeType: String? = null
    ) {
        val uri: Uri = when (format) {
            RomFormat.FILE_PATH -> Uri.fromFile(File(romPath))
            RomFormat.DOCUMENT_URI -> LaunchProfiles.documentUri(romPath, romRoot) ?: shareUri(context, romPath)
            RomFormat.SHARED_URI, RomFormat.AUTO -> shareUri(context, romPath)
        }
        if (extraKey != null) {
            intent.putExtra(extraKey, if (format == RomFormat.FILE_PATH) romPath else uri.toString())
        } else {
            intent.setDataAndType(uri, mimeType ?: guessMimeType(romPath))
        }
        // Links Shelf shares itself: the emulator gets read access to exactly
        // this file. A grant flag alone only covers the intent's data, so for
        // extras the link also rides along as clip data and is granted
        // directly to the emulator.
        if (uri.authority == "${context.packageName}.fileprovider") {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (intent.clipData == null) intent.clipData = ClipData.newRawUri("", uri)
            if (targetPackage != null) {
                runCatching {
                    context.grantUriPermission(targetPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }

    /** A content:// link to the file through Shelf's own FileProvider, which
     * any emulator can open once it's granted — no folder access needed on
     * its side. Falls back to a plain file:// link if the file can't be
     * shared. */
    private fun shareUri(context: Context, path: String): Uri {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        } catch (e: Exception) {
            Uri.fromFile(File(path))
        }
    }

    private var fileUrisAllowed = false

    /**
     * Turns off Android's FileUriExposedException check so file:// ROM paths
     * can be handed to emulators — the same thing other emulator front-ends
     * do, since emulators hold their own storage access and read real paths.
     * Only the VM policy is reset (no other checks are configured in this app).
     */
    private fun allowFileUriIntents() {
        if (fileUrisAllowed) return
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        fileUrisAllowed = true
    }

    private fun guessMimeType(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "zip" -> "application/zip"
        "nes" -> "application/x-nes-rom"
        "sfc", "smc" -> "application/x-snes-rom"
        "gba" -> "application/x-gba-rom"
        "z64", "n64" -> "application/x-n64-rom"
        "bin", "iso", "cue", "chd", "rvz", "gcz", "wbfs", "ciso", "cso", "pbp" -> "application/octet-stream"
        else -> null
    }
}
