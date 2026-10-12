package com.shelf.launcher.data

import android.content.Context

/** Your own pictures for tiles on the category screen (category key → image URI). */
class CategoryImagesStore(context: Context) {
    private val prefs = context.getSharedPreferences("category_images", Context.MODE_PRIVATE)

    fun loadAll(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun set(key: String, uri: String?) {
        prefs.edit().apply { if (uri == null) remove(key) else putString(key, uri) }.apply()
    }
}
