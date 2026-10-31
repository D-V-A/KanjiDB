package com.example.kanjidb.ui.lists

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/** SAF location hint only: DocumentsUI chooses its normal location if unavailable.
 * No raw-path access, directory grants, provider-specific raw IDs or storage permissions.
 */
internal class UserDataOpenDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/KanjiDB"))
}
