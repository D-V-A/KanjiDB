package com.example.kanjidb.data.user

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UserDataExportTest {
    private lateinit var provider: ExportMediaProvider
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        provider = ExportMediaProvider()
        provider.folder = File("build/tmp/export-tests/${UUID.randomUUID()}").apply { mkdirs() }
        provider.attachInfo(context, ProviderInfo().apply { authority = "media"; exported = true })
        ShadowContentResolver.registerProviderInternal("media", provider)
    }
    @After fun cleanup() {
        provider.folder.listFiles().orEmpty().forEach { it.delete() }
        provider.folder.delete()
    }

    @Test fun filenameNormalizationKeepsOneExtensionAndTrimsWhitespace() {
        assertEquals("test.kdb", normalizedDataFileName("test"))
        assertEquals("test.kdb", normalizedDataFileName("  test  "))
        assertEquals("test.kdb", normalizedDataFileName(" test.KDB "))
        assertEquals("test.kdb", normalizedDataFileName("test.kdb.kdb"))
        assertEquals("日本.kdb", normalizedDataFileName(" 日本 "))
    }
    @Test fun emptyOrPathLikeFileNamesRejected() {
        for (name in listOf("", " ", ".kdb", ".kdb.kdb", ".", "..", "../test", "a/b", "a\\b", "a\nb")) {
            assertThrows(BackupException::class.java) { normalizedDataFileName(name) }
        }
    }
    @Test fun targetsAndFeedbackDistinguishDownloadsRootFromSubfolder() {
        assertEquals("Download/KanjiDB/", dataExportRelativePath(true))
        assertEquals("Download/", dataExportRelativePath(false))
        assertEquals("test.kdb saved to Downloads/KanjiDB.", dataExportSuccessMessage("test.kdb", true))
        assertEquals("test.kdb saved to Downloads.", dataExportSuccessMessage("test.kdb", false))
    }
    @Test fun writesUnicodeJsonWhilePendingAndPublishesOnlyCompleteFile() {
        val json = "{\"character\":\"日\"}"
        writeUserDataDownload(context.contentResolver, true, "Data.kdb", json)
        val row = provider.rows.values.single()
        assertEquals(json, row.file.readText(Charsets.UTF_8))
        assertEquals("application/octet-stream", row.values.getAsString(MediaStore.Downloads.MIME_TYPE))
        assertEquals("Download/KanjiDB/", row.values.getAsString(MediaStore.Downloads.RELATIVE_PATH))
        assertEquals(0, row.values.getAsInteger(MediaStore.Downloads.IS_PENDING))
        assertTrue(provider.openedWhilePending)
        assertEquals(listOf(json), provider.publishedContents)
    }
    @Test fun uncheckedTargetWritesToDownloadsRoot() {
        writeUserDataDownload(context.contentResolver, false, "Data.kdb", "data")
        assertEquals("Download/", provider.rows.values.single().values.getAsString(MediaStore.Downloads.RELATIVE_PATH))
    }
    @Test fun collisionIsBoundToNameAndTargetPathAndDoesNotOverwrite() {
        val original = provider.seed("Data.kdb", "Download/KanjiDB/", "original")
        val error = assertThrows(BackupException::class.java) {
            writeUserDataDownload(context.contentResolver, true, "Data.kdb", "replacement")
        }
        assertEquals("A file with this name already exists.", error.message)
        assertEquals(0, provider.creations)
        assertEquals("original", original.file.readText())
        assertEquals(listOf("Data.kdb", "Download/KanjiDB/"), provider.lastSelectionArgs)
        assertEquals("_display_name = ? AND relative_path = ?", provider.lastSelection)
        writeUserDataDownload(context.contentResolver, false, "Data.kdb", "root data")
        assertEquals(2, provider.rows.size)
        assertEquals("original", original.file.readText())
    }
    @Test fun existingRootNameDoesNotBlockSubfolder() {
        provider.seed("Data.kdb", "Download/", "original")
        writeUserDataDownload(context.contentResolver, true, "Data.kdb", "subfolder data")
        assertEquals(2, provider.rows.size)
    }
    @Test fun providerAutoRenameIsRejectedAndNewRowDeletedBeforeWriting() {
        provider.renameOnInsert = true
        assertThrows(BackupException::class.java) {
            writeUserDataDownload(context.contentResolver, true, "Data.kdb", "data")
        }
        assertTrue(provider.rows.isEmpty())
        assertEquals(1, provider.deleted)
        assertFalse(provider.openedWhilePending)
        assertTrue(provider.publishedContents.isEmpty())
    }
    @Test fun writeFailureDeletesOnlyNewRowAndDoesNotPublish() {
        val original = provider.seed("Existing.kdb", "Download/KanjiDB/", "original")
        provider.failWrite = true
        assertThrows(Exception::class.java) { writeUserDataDownload(context.contentResolver, true, "New.kdb", "data") }
        assertEquals(1, provider.creations)
        assertEquals(1, provider.deleted)
        assertEquals(listOf(original), provider.rows.values.toList())
        assertEquals("original", original.file.readText())
        assertTrue(provider.publishedContents.isEmpty())
    }
    @Test fun publishingFailureAlsoRemovesCompleteButUnpublishedRow() {
        provider.failPublish = true
        assertThrows(BackupException::class.java) { writeUserDataDownload(context.contentResolver, true, "Data.kdb", "data") }
        assertEquals(1, provider.deleted)
        assertTrue(provider.rows.isEmpty())
    }
    @Test fun listingFailureNeverCreatesAFile() {
        provider.failQuery = true
        assertThrows(Exception::class.java) { writeUserDataDownload(context.contentResolver, true, "New.kdb", "data") }
        assertEquals(1, provider.listingAttempts)
        assertEquals(0, provider.creations)
    }

    class ExportMediaProvider : ContentProvider() {
        data class Row(val id: Long, val file: File, val values: ContentValues)
        lateinit var folder: File
        val rows = linkedMapOf<Long, Row>()
        val publishedContents = mutableListOf<String>()
        var failWrite = false
        var failQuery = false
        var failPublish = false
        var renameOnInsert = false
        var creations = 0
        var deleted = 0
        var listingAttempts = 0
        var openedWhilePending = false
        var lastSelection: String? = null
        var lastSelectionArgs = emptyList<String>()
        private var nextId = 1L
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/octet-stream"
        fun seed(name: String, path: String, contents: String): Row = add(ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, path)
            put(MediaStore.Downloads.IS_PENDING, 0)
        }).also { it.file.writeText(contents, Charsets.UTF_8) }
        private fun add(values: ContentValues): Row {
            val id = nextId++
            return Row(id, File(folder, id.toString()).apply { writeText("") }, ContentValues(values)).also { rows[id] = it }
        }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val id = uri.lastPathSegment?.toLongOrNull()
            val matches = if (id != null) listOfNotNull(rows[id]) else {
                listingAttempts++
                if (failQuery) throw FileNotFoundException("Cannot query Downloads")
                lastSelection = selection
                lastSelectionArgs = selectionArgs.orEmpty().toList()
                rows.values.filter {
                    it.values.getAsString(MediaStore.Downloads.DISPLAY_NAME) == selectionArgs?.get(0) &&
                        it.values.getAsString(MediaStore.Downloads.RELATIVE_PATH) == selectionArgs?.get(1)
                }
            }
            val columns = requireNotNull(projection)
            return MatrixCursor(columns).apply {
                matches.forEach { row -> addRow(columns.map {
                    if (it == MediaStore.Downloads._ID) row.id else row.values.get(it)
                }) }
            }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            creations++
            val data = ContentValues(requireNotNull(values))
            if (renameOnInsert) data.put(MediaStore.Downloads.DISPLAY_NAME, "Data (1).kdb")
            return ContentUris.withAppendedId(uri, add(data).id)
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (failWrite) throw FileNotFoundException("Cannot write file")
            val row = rows.getValue(ContentUris.parseId(uri))
            openedWhilePending = row.values.getAsInteger(MediaStore.Downloads.IS_PENDING) == 1
            return ParcelFileDescriptor.open(row.file, ParcelFileDescriptor.parseMode(mode))
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (failPublish) return 0
            val row = rows.getValue(ContentUris.parseId(uri))
            publishedContents += row.file.readText(Charsets.UTF_8)
            row.values.putAll(requireNotNull(values))
            return 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val row = rows.remove(ContentUris.parseId(uri)) ?: return 0
            deleted++
            row.file.delete()
            return 1
        }
    }
}
