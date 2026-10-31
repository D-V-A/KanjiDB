package com.example.kanjidb.ui.lists

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UserDataOpenDocumentTest {
    @Test fun locationIsOnlyAHintAndTheStandardOpenDocumentContractIsRetained() {
        val intent = UserDataOpenDocument().createIntent(RuntimeEnvironment.getApplication(), arrayOf("*/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        val standard = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            .createIntent(RuntimeEnvironment.getApplication(), arrayOf("*/*"))
        assertEquals(standard.categories, intent.categories)
        assertEquals("*/*", intent.type)
        assertArrayEquals(arrayOf("*/*"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        @Suppress("DEPRECATION")
        val initial = intent.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI)
        assertNotNull(initial)
        assertEquals("primary:Download/KanjiDB", DocumentsContract.getDocumentId(initial!!))
        assertNull(intent.`package`)
        assertNull(intent.component)
        assertNull(intent.data)
    }
    @Test fun otherLocationsAndCancellationKeepTheOriginalResultHandling() {
        val contract = UserDataOpenDocument()
        val elsewhere = Uri.parse("content://another.provider/document/other-folder/file.kdb")
        assertEquals(elsewhere, contract.parseResult(Activity.RESULT_OK, Intent().setData(elsewhere)))
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, Intent().setData(elsewhere)))
        assertNull(contract.parseResult(Activity.RESULT_OK, null))
    }
}
