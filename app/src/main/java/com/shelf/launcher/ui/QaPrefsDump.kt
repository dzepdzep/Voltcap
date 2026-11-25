package com.shelf.launcher.ui

import android.content.Context
import java.io.File

/**
 * 682bd, QA ONLY (owner 25 Nov 21:24 - upgrade / persistence audit): `--es voltcap_test_prefsdump <tag>` writes every
 * settings file of the isolated QA app - every key with its type and value - to <its external files>/prefsdump/<tag>.json,
 * so a script can compare the settings before and after a restart, a rescan, a layout change or an upgrade
 * (tools/qa/tools/persistdiff.py). The QA app is not debuggable (no run-as), so it says itself what it holds. Sign-in stores
 * (SettingsScopes AUTH) are listed by key with a hash of the value only. Nothing is changed. The real app never runs it.
 */
object QaPrefsDump {
    fun dump(context: Context, tag: String): File? {
        if (!com.shelf.launcher.data.QaVariant.isolated(context) || !tag.matches(Regex("[A-Za-z0-9_.-]{1,60}"))) return null
        val app = context.applicationContext
        val dir = File(app.applicationInfo.dataDir, "shared_prefs")
        val out = org.json.JSONObject()
        dir.listFiles().orEmpty().filter { it.name.endsWith(".xml") }.sortedBy { it.name }.forEach { f ->
            val name = f.name.removeSuffix(".xml")
            val auth = com.shelf.launcher.data.SettingsScopes.scopeOf(name) == com.shelf.launcher.data.SettingsScope.AUTH
            val vals = org.json.JSONObject()
            runCatching { app.getSharedPreferences(name, Context.MODE_PRIVATE).all }.getOrNull().orEmpty().toSortedMap().forEach { (k, v) ->
                val typed = when (v) {
                    is Boolean -> "b:$v"; is Int -> "i:$v"; is Long -> "l:$v"; is Float -> "f:$v"
                    is Set<*> -> "set:" + v.map { it.toString() }.sorted().joinToString("|")
                    else -> "s:" + v.toString()
                }
                vals.put(k, if (auth) "hash:" + typed.hashCode() else typed)
            }
            out.put(name, vals)
        }
        val target = File(app.getExternalFilesDir("prefsdump"), "$tag.json")
        target.writeText(out.toString(1))
        android.util.Log.i("VoltcapQA", "prefsdump $tag -> ${out.length()} files")
        return target
    }
}
