package com.example.kanjidb.data.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** null means missing or unreadable; dictionary releases never downgrade local data. */
internal fun shouldInstallDictionary(localVersion: Int?, bundledVersion: Int): Boolean {
    require(bundledVersion > 0)
    return localVersion == null || localVersion < bundledVersion
}

internal fun parseDictionaryVersion(text: String): Int {
    val value = text.trim()
    check(value.matches(Regex("[0-9]+"))) { "Invalid bundled dictionary version" }
    return checkNotNull(value.toIntOrNull()?.takeIf { it > 0 }) {
        "Invalid bundled dictionary version"
    }
}

/** Only installs before the first query handle opens, shared by all instances in this process. */
internal object DictionaryInstallation {
    private val readyFiles = mutableSetOf<String>()

    @Synchronized
    fun file(context: Context): File {
        val target = File(context.noBackupFilesDir, "dictionary.db")
        if (target.absolutePath in readyFiles) return target

        // Fail before touching a usable local copy if APK metadata is missing or malformed.
        val bundledVersion = context.assets.open("dictionary_version.txt").bufferedReader().use {
            parseDictionaryVersion(it.readText())
        }
        val localVersion = if (target.exists()) {
            try {
                val version = readVersion(target)
                if (version == bundledVersion) readVersion(target, validateWords = true) else version
            } catch (error: Exception) {
                Log.w("DictionaryInstallation", "Unreadable local dictionary; restoring bundled copy", error)
                null
            }
        } else null
        if (shouldInstallDictionary(localVersion, bundledVersion)) {
            replaceDictionary(target, bundledVersion,
                copy = { temporary ->
                    context.assets.open("dictionary.db").use { input ->
                        temporary.outputStream().use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                }, validate = { readVersion(it, validateWords = true) })
        } else if (localVersion!! > bundledVersion) {
            Log.w("DictionaryInstallation", "Keeping newer local dictionary v$localVersion; bundled v$bundledVersion")
        }
        readyFiles.add(target.absolutePath)
        return target
    }

    fun readVersion(file: File, validateWords: Boolean = false): Int = SQLiteDatabase.openDatabase(
        file.absolutePath, null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        // Avoid Android's default corruption handler deleting a file before replacement is ready.
        { throw IllegalStateException("Invalid dictionary: ${file.name}") }
    ).use { db ->
        // Schema access detects non-dictionary SQLite files without scanning the large database.
        db.rawQuery("SELECT id, character FROM kanji LIMIT 1", null).use {
            check(it.moveToFirst()) { "Dictionary has no kanji" }
        }
        val tables = listOf("kanji_meaning", "kanji_reading", "jlpt_kanji") + if(validateWords)
            listOf("word_entry", "word_written", "word_entry_written", "word_reading", "word_entry_written_reading", "word_sense", "word_meaning", "word_sense_form", "word_written_kanji") else emptyList()
        for (table in tables) {
            db.rawQuery("SELECT 1 FROM $table LIMIT 1", null).use { it.moveToFirst() }
        }
        db.rawQuery("PRAGMA user_version", null).use {
            check(it.moveToFirst()) { "Cannot read dictionary version" }
            it.getInt(0).also { version -> check(version >= 0) { "Invalid local dictionary version" } }
        }
    }
}

/** The old file survives copy/validation/rename failures; no working query handles are open yet. */
internal fun replaceDictionary(
    target: File,
    bundledVersion: Int,
    copy: (File) -> Unit,
    validate: (File) -> Int,
    install: (File, File) -> Unit = { temporary, destination ->
        Files.move(temporary.toPath(), destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
) {
    val temporary = File.createTempFile("dictionary-", ".tmp", target.parentFile)
    try {
        copy(temporary)
        check(validate(temporary) == bundledVersion) { "Bundled dictionary database/version asset mismatch" }
        install(temporary, target)
    } finally {
        temporary.delete()
        listOf("-journal", "-wal", "-shm").forEach { File(temporary.path + it).delete() }
    }
}
