package com.crosspaste.ui.theme

enum class DesktopSearchWindowStyle(val style: String) {

    CENTER_STYLE("center"),
    SIDE_STYLE("side"),
    ;

    companion object {
        fun isCenterStyle(style: String): Boolean {
            return style == CENTER_STYLE.style
        }

        fun isSideStyle(style: String): Boolean {
            return style == SIDE_STYLE.style
        }
    }
}
