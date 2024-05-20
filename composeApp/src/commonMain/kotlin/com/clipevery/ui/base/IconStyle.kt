package com.clipevery.ui.base

interface IconStyle {

    fun isMacStyleIcon(source: String): Boolean

    fun refreshStyle(source: String)
}
