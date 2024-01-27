package com.clipevery.ui

class PageViewContext(val pageViewType: PageViewType, val context: Any) {

    constructor(pageViewType: PageViewType) : this(pageViewType, Unit)
}

enum class PageViewType {
    HOME,
    SETTINGS,
    ABOUT,
    DEVICE_DETAIL
}