package com.example.kanjidb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Tabler Icons, MIT License. See assets/licenses/tabler-icons.txt for the complete notice.
 * Stroke geometry is color-neutral; Material Icon applies the current content color.
 */
internal object AppIcons {
    val TextQuestion by lazy { strokeIcon("TextQuestion",
        "M10 8v6a2 2 0 1 0 4 0v-1a2 2 0 1 0 -4 0v1",
        "M7 16v-3a2 2 0 1 0 -4 0v1a2 2 0 0 0 3.726 1.01",
        "M19 16v.01",
        "M19 13a2 2 0 0 0 .914 -3.782a1.98 1.98 0 0 0 -2.414 .483") }

    val Backspace by lazy { strokeIcon("Backspace",
        "M20 6a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1h-11l-5 -5a1.5 1.5 0 0 1 0 -2l5 -5l11 0",
        "M12 10l4 4m0 -4l-4 4") }

    val Search by lazy { strokeIcon("Search",
        "M3 10a7 7 0 1 0 14 0a7 7 0 1 0 -14 0",
        "M21 21l-6 -6") }

    val Vocabulary by lazy { strokeIcon("Vocabulary",
        "M10 19h-6a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1h6a2 2 0 0 1 2 2a2 2 0 0 1 2 -2h6a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-6a2 2 0 0 0 -2 2a2 2 0 0 0 -2 -2",
        "M12 5v16",
        "M7 7h1",
        "M7 11h1",
        "M16 7h1",
        "M16 11h1",
        "M16 15h1") }

    val PencilCheck by lazy { strokeIcon("PencilCheck",
        "M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4",
        "M13.5 6.5l4 4",
        "M15 19l2 2l4 -4") }

    val Eye by lazy { strokeIcon("Eye",
        "M10 12a2 2 0 1 0 4 0a2 2 0 0 0 -4 0",
        "M21 12c-2.4 4 -5.4 6 -9 6c-3.6 0 -6.6 -2 -9 -6c2.4 -4 5.4 -6 9 -6c3.6 0 6.6 2 9 6") }

    val Brush by lazy { strokeIcon("Brush",
        "M3 21v-4a4 4 0 1 1 4 4h-4",
        "M21 3a16 16 0 0 0 -12.8 10.2",
        "M21 3a16 16 0 0 1 -10.2 12.8",
        "M10.6 9a9 9 0 0 1 4.4 4.4") }

    val Notebook by lazy { strokeIcon("Notebook",
        "M6 4h11a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-11a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1m3 0v18",
        "M13 8l2 0",
        "M13 12l2 0") }

    val ListLetters by lazy { strokeIcon("ListLetters",
        "M11 6h9",
        "M11 12h9",
        "M11 18h9",
        "M4 10v-4.5a1.5 1.5 0 0 1 3 0v4.5",
        "M4 8h3",
        "M4 20h1.5a1.5 1.5 0 0 0 0 -3h-1.5h1.5a1.5 1.5 0 0 0 0 -3h-1.5v6") }

    val ListSearch by lazy { strokeIcon("ListSearch",
        "M11 15a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        "M18.5 18.5l2.5 2.5",
        "M4 6h16",
        "M4 12h4",
        "M4 18h4") }

    val FilterDown by lazy { strokeIcon("FilterDown",
        "M12 20l-3 1v-8.5l-4.48 -4.928a2 2 0 0 1 -.52 -1.345v-2.227h16v2.172a2 2 0 0 1 -.586 1.414l-4.414 4.414v3",
        "M19 16v6",
        "M22 19l-3 3l-3 -3") }

    val FilterUp by lazy { strokeIcon("FilterUp",
        "M12 20l-3 1v-8.5l-4.48 -4.928a2 2 0 0 1 -.52 -1.345v-2.227h16v2.172a2 2 0 0 1 -.586 1.414l-4.414 4.414v2",
        "M19 22v-6",
        "M22 19l-3 -3l-3 3") }

    val Sheets by lazy { strokeIcon("Sheets",
        "M7 9.667a2.667 2.667 0 0 1 2.667 -2.667h8.666a2.667 2.667 0 0 1 2.667 2.667v8.666a2.667 2.667 0 0 1 -2.667 2.667h-8.666a2.667 2.667 0 0 1 -2.667 -2.667l0 -8.666",
        "M4.012 16.737a2.005 2.005 0 0 1 -1.012 -1.737v-10c0 -1.1 .9 -2 2 -2h10c.75 0 1.158 .385 1.5 1") }

    val TransferVertical by lazy { strokeIcon("TransferVertical",
        "M10 4v16l-6 -5.5",
        "M14 20v-16l6 5.5") }

    val SortDescending by lazy { strokeIcon("SortDescending",
        "M4 6h16",
        "M6 12h12",
        "M9 18h6") }

    // Approved correction: increasing 6/12/16 bars within the 24x24 viewport.
    val SortAscending by lazy { strokeIcon("SortAscending",
        "M9 6h6",
        "M6 12h12",
        "M4 18h16") }

    val CircleChevronLeft by lazy { strokeIcon("CircleChevronLeft",
        "M13 15l-3 -3l3 -3",
        "M21 12a9 9 0 1 0 -18 0a9 9 0 0 0 18 0") }

    val FilterOff by lazy { strokeIcon("FilterOff",
        "M8 4h12v2.172a2 2 0 0 1 -.586 1.414l-3.914 3.914m-.5 3.5v4l-6 2v-8.5l-4.48 -4.928a2 2 0 0 1 -.52 -1.345v-2.227",
        "M3 3l18 18") }

    val CirclePlay by lazy { strokeIcon("CirclePlay",
        "M9 9.003a1 1 0 0 1 1.517 -.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997a1 1 0 0 1 -1.517 -.86v-5.993",
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0") }

    val CircleX by lazy { strokeIcon("CircleX",
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
        "M10 10l4 4m0 -4l-4 4") }

    val InfoSquareRounded by lazy { strokeIcon("InfoSquareRounded",
        "M12 9h.01",
        "M11 12h1v4h1",
        "M12 3c7.2 0 9 1.8 9 9c0 7.2 -1.8 9 -9 9c-7.2 0 -9 -1.8 -9 -9c0 -7.2 1.8 -9 9 -9") }

    val Cross by lazy { strokeIcon("Cross",
        "M18 6l-12 12",
        "M6 6l12 12") }

    val Check by lazy { strokeIcon("Check",
        "M5 12l5 5l10 -10") }

    val AlertCircle by lazy { strokeIcon("AlertCircle",
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
        "M12 8v4",
        "M12 16h.01") }

    val Rotate by lazy { strokeIcon("Rotate",
        "M19.95 11a8 8 0 1 0 -.5 4m.5 5v-5h-5") }

    val CircleCheck by lazy { strokeIcon("CircleCheck",
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
        "M9 12l2 2l4 -4") }

    val Refresh by lazy { strokeIcon("Refresh",
        "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4",
        "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4") }

    val RefreshMain by lazy { strokeIcon("refresh-main",
        "M20 11 a8.1 8.1 0 0 0 -15.5 -2 m0.209 -3.995 l-0.209 3.995 l3.564 -1.816",
        "M4 13 a8.1 8.1 0 0 0 15.5 2 m-0.209 3.995 l0.209 -3.995 l-3.564 1.816") }

    val RefreshDot by lazy { strokeIcon("RefreshDot",
        "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4",
        "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4",
        "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }

    val RefreshAlert by lazy { strokeIcon("RefreshAlert",
        "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4",
        "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4",
        "M12 9l0 3",
        "M12 15l.01 0") }

    val Repeat by lazy { strokeIcon("Repeat",
        "M4 12v-3a3 3 0 0 1 3 -3h13m-3 -3l3 3l-3 3",
        "M20 12v3a3 3 0 0 1 -3 3h-13m3 3l-3 -3l3 -3") }

    val CornerDownLeftDouble by lazy { strokeIcon("CornerDownLeftDouble",
        "M19 5v6a3 3 0 0 1 -3 3h-7",
        "M13 10l-4 4l4 4m-5 -8l-4 4l4 4") }

    private fun strokeIcon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { data ->
                addPath(PathParser().parsePathString(data).toNodes(), fill = null,
                    stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()
}

/** Decorative icon beside existing text; the control's text supplies its accessible label. */
@Composable
internal fun IconLabel(icon: ImageVector, trailing: Boolean = false, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!trailing) AppControlIcon(icon)
        content()
        if (trailing) AppControlIcon(icon)
    }
}

@Composable
internal fun AppControlIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
}
