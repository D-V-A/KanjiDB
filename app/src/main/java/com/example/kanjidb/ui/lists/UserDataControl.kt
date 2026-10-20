package com.example.kanjidb.ui.lists

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kanjidb.data.dictionary.DictionaryDatabase
import com.example.kanjidb.data.user.*
import com.example.kanjidb.ui.AppIcons
import com.example.kanjidb.ui.IconLabel
import kotlinx.coroutines.*
import java.time.Instant
import java.time.LocalDate

/** Process lifetime, like TrainingState: recreation cannot cancel an in-flight replacement. */
private object UserDataOperations {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var busy by mutableStateOf(false)
        private set
    var pending by mutableStateOf<UserBackup?>(null)
    var missing by mutableStateOf(emptyList<String>())
        private set
    var message by mutableStateOf<String?>(null)
    var namingExport by mutableStateOf(false)
    var exportBasename by mutableStateOf("")
    var exportSubfolder by mutableStateOf(true)
    var exportError by mutableStateOf<String?>(null)
    private var exportName: String? = null

    fun beginExport() {
        exportBasename = "KanjiDB_Data_${LocalDate.now()}"
        exportSubfolder = true
        exportError = null
        namingExport = true
    }

    fun confirmExportName(): Boolean = try {
        exportName = normalizedDataFileName(exportBasename)
        exportBasename = requireNotNull(exportName).dropLast(4)
        exportError = null
        namingExport = false
        true
    } catch (error: BackupException) {
        exportError = error.message
        false
    }

    private fun run(failure: String, onFailure: ((String) -> Unit)? = null, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { operation() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                android.util.Log.e("UserBackup", failure, error)
                val copy = (error as? BackupException)?.message ?: failure
                if (onFailure != null) onFailure(copy) else message = copy
            } finally { busy = false }
        }
    }

    fun export(context: Context) = run("Could not save the data file. Please try again.", onFailure = {
        exportError = it
        namingExport = true
    }) {
        val subfolder = exportSubfolder
        val fileName = requireNotNull(exportName)
        withContext(Dispatchers.IO) {
            val data = UserDatabase.getInstance(context).backup().snapshot()
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            val backup = UserBackup(info.versionName.orEmpty(), info.versionCode.toLong(), Instant.now().toString(), data)
            val json = UserBackupFormat.encode(backup)
            writeUserDataDownload(context.contentResolver, subfolder, fileName, json)
        }
        message = dataExportSuccessMessage(fileName, subfolder)
    }

    fun inspect(context: Context, uri: Uri) = run("Could not open the data file. Please try again.") {
        pending = null
        val result = withContext(Dispatchers.IO) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            UserBackupFormat.validateFileName(name)
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw BackupException("The selected data file cannot be opened.")
            val bytes = stream.use { it.readBytes() }
            val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            val backup = UserBackupFormat.decode(text)
            val available = DictionaryDatabase(context).getKanjiGroups().mapTo(hashSetOf()) { it.character }
            backup to backup.data.missingCharacters(available)
        }
        missing = result.second
        pending = result.first
    }

    fun restore(context: Context) {
        val backup = pending ?: return
        run("Could not import the data file. Your current user data has been preserved.") {
            withContext(Dispatchers.IO) { UserDatabase.getInstance(context).backup().replace(backup.data) }
            pending = null
            message = "Data file imported."
        }
    }
}

@Composable
internal fun UserDataControl(snackbar: SnackbarHostState) {
    val context = LocalContext.current.applicationContext
    val dao = remember(context) { UserDatabase.getInstance(context).backup() }
    val hasData by remember(dao) { dao.observeHasData() }.collectAsStateWithLifecycle(false)
    var menu by rememberSaveable { mutableStateOf(false) }
    var showMissing by rememberSaveable { mutableStateOf(false) }
    val busy = UserDataOperations.busy
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) UserDataOperations.inspect(context, it)
    }
    val message = UserDataOperations.message
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message, duration = if (message == UserBackupFormat.INVALID_DATA_FILE)
                SnackbarDuration.Long else SnackbarDuration.Short)
            if (UserDataOperations.message == message) UserDataOperations.message = null
        }
    }
    IconButton(onClick = { menu = true }) { Icon(AppIcons.FileDatabase, "User data") }
    if (menu) com.example.kanjidb.ui.ActionMenuDialog("User data", { menu = false }, dismissEnabled = !busy) {
        OutlinedButton(onClick = {
            menu = false
            UserDataOperations.beginExport()
        }, enabled = hasData && !busy, modifier = Modifier.fillMaxWidth()) {
            IconLabel(AppIcons.DatabaseShare) { Text("Export") }
        }
        OutlinedButton(onClick = { menu = false; importLauncher.launch(arrayOf("*/*")) },
            enabled = !busy, modifier = Modifier.fillMaxWidth()) { IconLabel(AppIcons.DatabasePlus) { Text("Import") } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (UserDataOperations.namingExport) SaveDataFileDialog(
        basename = UserDataOperations.exportBasename,
        error = UserDataOperations.exportError,
        subfolder = UserDataOperations.exportSubfolder,
        onSubfolderChange = { UserDataOperations.exportSubfolder = it; UserDataOperations.exportError = null },
        onNameChange = { UserDataOperations.exportBasename = it; UserDataOperations.exportError = null },
        onDismiss = { UserDataOperations.namingExport = false },
        onSave = {
            if (UserDataOperations.confirmExportName()) {
                UserDataOperations.export(context)
            }
        })
    if (busy && !menu && UserDataOperations.pending == null) AlertDialog(
        onDismissRequest = {}, title = { Text("User data") },
        text = { Column { Text("Please wait…"); LinearProgressIndicator(Modifier.fillMaxWidth()) } },
        confirmButton = {})
    if (UserDataOperations.pending != null) AlertDialog(
        onDismissRequest = { if (!busy) UserDataOperations.pending = null },
        title = { Text("Import data file?") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Importing this data file will replace your current user data.")
                if (UserDataOperations.missing.isNotEmpty()) {
                    Text("Some kanji in this data file are not available in the current dictionary. They will be preserved, but dictionary details and training will be unavailable for them.")
                    TextButton(onClick = { showMissing = true }) { Text("Show missing kanji") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!busy) UserDataOperations.message?.let { UserDataMessage(it) }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { UserDataOperations.pending = null }) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = !busy, onClick = { UserDataOperations.restore(context) }) { Text("Import") } })
    if (showMissing && UserDataOperations.pending != null) AlertDialog(
        onDismissRequest = { showMissing = false }, title = { Text("Missing kanji") },
        text = { Text(UserDataOperations.missing.joinToString(", "),
            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showMissing = false }) { Text("Close") } })
}

/** The entire form scrolls within the IME-adjusted window, including actions on short screens. */
@Composable
private fun SaveDataFileDialog(
    basename: String, error: String?, subfolder: Boolean, onSubfolderChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Save data file", style = MaterialTheme.typography.headlineSmall)
                    OutlinedTextField(value = basename, onValueChange = onNameChange,
                        label = { Text("File name") }, suffix = { Text(".kdb") }, singleLine = true,
                        isError = error != null, modifier = Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(Modifier.fillMaxWidth().toggleable(value = subfolder, role = Role.Checkbox,
                        onValueChange = onSubfolderChange), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = subfolder, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save to KanjiDB subfolder")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(onClick = onSave) { Text("Save") }
                    }
                }
            }
        }
    }
}

/** Prefer the sentence boundary if the complete validation message needs wrapping. */
@Composable
internal fun UserDataMessage(message: String) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current
    BoxWithConstraints {
        val splitSentences = message == UserBackupFormat.INVALID_DATA_FILE &&
            measurer.measure(message, style = style, maxLines = 1,
                constraints = Constraints(maxWidth = constraints.maxWidth)).hasVisualOverflow
        Text(if (splitSentences) message.replace(". Please", ".\nPlease") else message)
    }
}
