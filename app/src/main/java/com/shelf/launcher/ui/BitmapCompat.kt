package com.shelf.launcher.ui

/**
 * Lint baseline work (12 Nov): `Bitmap.Config.HARDWARE` exists from Android 8 (API 26); Voltcap runs from Android 7 (minSdk 24),
 * where reading that enum field throws NoSuchFieldError. Every "is this a hardware bitmap?" check goes through here: the same
 * answer on 26+, and false before (there are no hardware bitmaps there).
 */
internal fun android.graphics.Bitmap.isHardwareBitmap(): Boolean =
    android.os.Build.VERSION.SDK_INT >= 26 && config == android.graphics.Bitmap.Config.HARDWARE
