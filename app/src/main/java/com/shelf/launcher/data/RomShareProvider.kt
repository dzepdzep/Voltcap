package com.shelf.launcher.data

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider

/**
 * Update 326: Voltcap's file provider (the "Shared file link" a game is handed to an emulator with), answering the
 * document questions emulators ask too.
 *
 * Found on the AYN Thor with Eden and Eden Duo: they ask a link for its document columns (mime_type, document_id —
 * Eden's FileUtil.isDirectory / exists). Android's FileProvider only knows a file's name and size and leaves every other
 * column out, so they logged "Couldn't read row 0, col 0" for every shared link. Here the columns a document has are
 * filled in (a file, its type from its name, its link as its id, never writable); the name and size still come from
 * FileProvider. Only columns that were asked for are returned, as before, so emulators that ask nothing more see no
 * change. (Eden's family still can't boot from a shared link — its game reader needs the folder it was given — so it
 * gets a storage link: EmulatorCatalog.profileFor.)
 */
class RomShareProvider : FileProvider() {

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val base = super.query(uri, projection, selection, selectionArgs, sortOrder)
        if (projection == null || projection.all { base.getColumnIndex(it) >= 0 }) return base
        // The one row FileProvider gave (name, size), read before it's closed.
        val known = HashMap<String, Any?>()
        if (base.moveToFirst()) {
            for (i in 0 until base.columnCount) {
                known[base.getColumnName(i)] = when (base.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> base.getLong(i)
                    Cursor.FIELD_TYPE_NULL -> null
                    else -> base.getString(i)
                }
            }
        }
        val hadRow = base.count > 0
        base.close()
        val out = MatrixCursor(projection, 1)
        if (!hadRow) return out
        val mime = runCatching { getType(uri) }.getOrNull() ?: "application/octet-stream"
        out.addRow(projection.map { col ->
            when {
                known.containsKey(col) -> known[col]
                col == DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
                col == DocumentsContract.Document.COLUMN_DOCUMENT_ID -> uri.toString()
                col == DocumentsContract.Document.COLUMN_FLAGS -> 0
                col == DocumentsContract.Document.COLUMN_LAST_MODIFIED -> null
                else -> null
            }
        }.toTypedArray())
        return out
    }
}
