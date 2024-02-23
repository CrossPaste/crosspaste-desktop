package com.clipevery.ui

class PageViewContext(val pageViewType: PageViewType, val nextPageViewContext: PageViewContext?, val context: Any) {

    constructor(pageViewType: PageViewType) : this(pageViewType, null, Unit)

    constructor(pageViewType: PageViewType, nextPageViewContext: PageViewContext) : this(pageViewType, nextPageViewContext, Unit)

    fun returnNext(): PageViewContext {
        return nextPageViewContext ?: PageViewContext(PageViewType.CLIP_PREVIEW)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PageViewContext

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
    CLIP_PREVIEW,
    DEVICE_PREVIEW,
    DEVICE_DETAIL,
    QR_CODE,
    SETTINGS,
    ABOUT,
    DEBUG
}