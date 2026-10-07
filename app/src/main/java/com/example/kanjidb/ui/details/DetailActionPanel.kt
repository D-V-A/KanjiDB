package com.example.kanjidb.ui.details

import com.example.kanjidb.ui.AppIcons
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.kanjidb.R
import com.example.kanjidb.ui.FloatingActionPanel

/** Both detail screens share the same return control, surface and action geometry. */
@Composable
internal fun DetailActionPanel(
    onReturnToOrigin: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit = {}
) {
    FloatingActionPanel(modifier) {
        OutlinedIconButton(onClick = onReturnToOrigin) {
            Icon(AppIcons.CornerDownLeftDouble, contentDescription = stringResource(R.string.details_return_to_origin))
        }
        content()
    }
}
