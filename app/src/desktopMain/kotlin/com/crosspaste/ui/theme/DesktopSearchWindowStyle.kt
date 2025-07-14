package com.crosspaste.ui.theme

enum class DesktopSearchWindowStyle(
    val style: String,
) {

    CENTER_STYLE("center"),
    SIDE_STYLE("side"),
    ;

    companion object {
        fun isCenterStyle(style: String): Boolean = style == CENTER_STYLE.style

        fun isSideStyle(style: String): Boolean = style == SIDE_STYLE.style
    }
}
