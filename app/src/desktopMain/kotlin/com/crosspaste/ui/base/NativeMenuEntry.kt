package com.crosspaste.ui.base

/** One row of a system context menu: a labelled command or a separator line. */
sealed interface NativeMenuEntry {
    class Item(
        val label: String,
        val action: () -> Unit,
    ) : NativeMenuEntry

    data object Separator : NativeMenuEntry
}
