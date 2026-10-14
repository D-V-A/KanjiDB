package com.example.kanjidb.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kanjidb.R
import com.example.kanjidb.ui.AppIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun rememberDetailsCopyAction(snackbar: SnackbarHostState): (String) -> Unit {
    val context=LocalContext.current
    val clipboard=remember(context) { checkNotNull(context.getSystemService(ClipboardManager::class.java)) }
    val scope=rememberCoroutineScope()
    val message=stringResource(R.string.details_copied)
    var feedback by remember { mutableStateOf<Job?>(null) }
    return remember(clipboard,scope,snackbar,message) { { text ->
        clipboard.setPrimaryClip(ClipData.newPlainText("KanjiDB",text))
        feedback?.cancel()
        feedback=scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message,duration=SnackbarDuration.Short)
        }
    } }
}

@Composable
internal fun DetailsCopyButton(text: String, label: String, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick={ onCopy(text) },modifier=modifier) {
        Icon(AppIcons.ClipboardCopy,contentDescription=label,modifier=Modifier.size(20.dp))
    }
}

/** Observe, never consume. Only an unhandled short background tap releases text focus.
 * SelectionContainer clears its selection/toolbar on focus loss. Buttons, selection
 * gestures, scrolling and popup windows retain their own pointer handling.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.clearSelectionOnBackgroundTap(): Modifier = composed {
    val focus=LocalFocusManager.current
    var allowTextMenu by remember { mutableStateOf(true) }
    // The outgoing platform toolbar can re-read its items before its hide job finishes.
    // Suppress that final refresh before focus/selection are cleared, then re-enable
    // on the next gesture so normal long-press/drag selection keeps its full menu.
    Modifier.filterTextContextMenuComponents { allowTextMenu }.pointerInput(focus) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Final)
            allowTextMenu=true
            var handled=down.isConsumed
            var moved=false
            var event=awaitPointerEvent(PointerEventPass.Final)
            do {
                handled=handled || event.changes.any { it.isConsumed } || event.changes.size!=1
                moved=moved || event.changes.any { (it.position-down.position).getDistance()>viewConfiguration.touchSlop }
                if(event.changes.none { it.pressed }) break
                event=awaitPointerEvent(PointerEventPass.Final)
            } while(true)
            val elapsed=event.changes.maxOf { it.uptimeMillis }-down.uptimeMillis
            if(!handled && !moved && elapsed<viewConfiguration.longPressTimeoutMillis) {
                allowTextMenu=false
                focus.clearFocus(force=true)
            }
        }
    }
}
