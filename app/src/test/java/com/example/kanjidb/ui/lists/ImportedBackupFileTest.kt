package com.example.kanjidb.ui.lists

import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.room.Room
import com.example.kanjidb.data.user.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ImportedBackupFileTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: BackupDocumentsProvider
    private val selected = DocumentsContract.buildDocumentUri("backup.test", "folder/selected.kdb")

    @Before fun registerProvider() {
        provider = BackupDocumentsProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "backup.test"
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        ShadowContentResolver.registerProviderInternal("backup.test", provider)
    }
    @Test fun deletesOnlyTheExactSelectedDocument() {
        assertTrue(deleteImportedBackupFile(context.contentResolver, selected))
        assertEquals(listOf("folder/selected.kdb"), provider.requests)
        assertEquals(setOf("other/selected.kdb", "folder/another.kdb"), provider.files)
    }
    @Test fun safPermissionAndProviderFailuresPreserveTheFile() {
        for (error in listOf(SecurityException("Read-only grant"), UnsupportedOperationException("Delete unsupported"),
                FileNotFoundException("Document unavailable"))) {
            provider.failure = error
            assertFalse(deleteImportedBackupFile(context.contentResolver, selected))
            assertTrue("folder/selected.kdb" in provider.files)
        }
    }
    @Test fun deletionFailureCannotUndoASuccessfulImport() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).allowMainThreadQueries().build()
        try {
            val incoming = FullBackupData(listOf(BackupState("\u65e5", "KNOWN", 8)),
                listOf(BackupList(1, "Imported", 2, listOf(BackupMember("\u65e5", 3)))),
                listOf(BackupMeaning("\u65e5", "day", "sun")))
            db.backup().replace(incoming)
            val imported = db.backup().snapshot()
            provider.failure = SecurityException("No write permission")
            assertFalse(deleteImportedBackupFile(context.contentResolver, selected))
            assertEquals(imported, db.backup().snapshot())
            assertEquals(incoming, imported)
        } finally { db.close() }
    }
}

class BackupDocumentsProvider : DocumentsProvider() {
    val files = mutableSetOf("folder/selected.kdb", "other/selected.kdb", "folder/another.kdb")
    val requests = mutableListOf<String>()
    var failure: Exception? = null
    override fun onCreate() = true
    override fun deleteDocument(documentId: String) {
        requests += documentId
        failure?.let { throw it }
        if (!files.remove(documentId)) throw FileNotFoundException(documentId)
    }
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("root_id"))
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("document_id"))
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = MatrixCursor(arrayOf("document_id"))
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = throw FileNotFoundException(documentId)
}
