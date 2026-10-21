package com.example.kanjidb.data.user

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.MediaStore

/** Accept an optional pasted extension while keeping a single fixed .kdb suffix in the UI. */
internal fun normalizedDataFileName(input: String): String {
    var basename = input.trim()
    while (basename.endsWith(".kdb", ignoreCase = true)) basename = basename.dropLast(4).trimEnd()
    if (basename.isBlank()) throw BackupException("Enter a file name.")
    if (basename == "." || basename == ".." || basename.any { it == '/' || it == '\\' || Character.isISOControl(it) })
        throw BackupException("Use a file name without path separators or control characters.")
    return "$basename.kdb"
}

internal fun dataExportRelativePath(subfolder: Boolean) = if (subfolder) "Download/KanjiDB/" else "Download/"

internal fun dataExportSuccessMessage(fileName: String, subfolder: Boolean) =
    "$fileName saved to Downloads${if (subfolder) "/KanjiDB" else ""}."

/** All queries and writes run on IO. Publish only a complete file; clean up only our new row. */
internal fun writeUserDataDownload(resolver: ContentResolver, subfolder: Boolean, fileName: String, json: String) {
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val path = dataExportRelativePath(subfolder)
    val collision = "A file with this name already exists."
    val existing = resolver.query(collection, arrayOf(MediaStore.Downloads._ID),
        "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
        arrayOf(fileName, path), null)
        ?: throw BackupException("Could not check existing data files. Please try again.")
    existing.use { cursor ->
        if (cursor.moveToFirst()) throw BackupException(collision)
    }
    val created = resolver.insert(collection, ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
        put(MediaStore.Downloads.RELATIVE_PATH, path)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }) ?: throw BackupException("Could not create the data file in Downloads.")
    try {
        // Scoped storage can hide another app's files, and another writer may race the
        // preflight query. Do not accept a provider-generated name such as "name (1).kdb".
        val actual = resolver.query(created, arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH),
            null, null, null) ?: throw BackupException("Could not verify the new data file.")
        actual.use {
            if (!it.moveToFirst()) throw BackupException("Could not verify the new data file.")
            if (it.getString(0) != fileName) throw BackupException(collision)
            if (it.getString(1) != path) throw BackupException("Could not save to the requested Downloads folder.")
        }
        val stream = resolver.openOutputStream(created, "w")
            ?: throw BackupException("The selected data file cannot be written.")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        val published = resolver.update(created, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        if (published != 1) throw BackupException("Could not finish saving the data file.")
    } catch (error: Exception) {
        try {
            if (resolver.delete(created, null, null) != 1)
                android.util.Log.w("UserBackup", "Could not remove incomplete export")
        } catch (cleanup: Exception) { android.util.Log.w("UserBackup", "Could not remove incomplete export", cleanup) }
        throw error
    }
}
