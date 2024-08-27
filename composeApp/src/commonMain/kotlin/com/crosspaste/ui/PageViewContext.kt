package com.crosspaste.ui

class PageViewContext(val pageViewType: PageViewType, val nextPageViewContext: PageViewContext?, val context: Any) {

    constructor(pageViewType: PageViewType) : this(pageViewType, null, Unit)

    constructor(pageViewType: PageViewType, nextPageViewContext: PageViewContext) : this(pageViewType, nextPageViewContext, Unit)

    fun returnNext(): PageViewContext {
        return nextPageViewContext ?: PageViewContext(PageViewType.PASTE_PREVIEW)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (other !is PageViewContext) return false

        if (pageViewType != other.pageViewType) return false
        if (nextPageViewContext != other.nextPageViewContext) return false
        if (context != other.context) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pageViewType.hashCode()
        result = 31 * result + (nextPageViewContext?.hashCode() ?: 0)
        result = 31 * result + context.hashCode()
        return result
    }
}

enum class PageViewType {
    PASTE_PREVIEW,
    DEVICES,
    DEVICE_DETAIL,
    QR_CODE,
    SETTINGS,
    SHORTCUT_KEYS,
    ABOUT,
    PASTE_TEXT_EDIT,
    DEBUG,
}
