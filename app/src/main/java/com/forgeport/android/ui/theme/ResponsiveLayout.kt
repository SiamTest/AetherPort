package com.forgeport.android.ui.theme

/** Count columns from window constraints and the user's text size, not device type. */
internal fun contentColumns(widthDp: Float, fontScale: Float, minCellDp: Float = 156f, gapDp: Float = 12f, maxColumns: Int = 6): Int {
    val scale = fontScale.coerceAtLeast(1f)
    return ((widthDp + gapDp) / (minCellDp * scale + gapDp)).toInt().coerceIn(1, maxColumns)
}

internal fun sideBySideGalleryHeader(widthDp: Float, fontScale: Float): Boolean =
    widthDp >= 110f + 16f + 180f * fontScale.coerceAtLeast(1f)
