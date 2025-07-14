package com.crosspaste.ui

class ScreenContext(
    val screenType: ScreenType,
    val nextScreenContext: ScreenContext?,
    val context: Any,
) {

    constructor(screenType: ScreenType) : this(screenType, null, Unit)

    constructor(screenType: ScreenType, nextScreenContext: ScreenContext) : this(screenType, nextScreenContext, Unit)

    fun returnNext(): ScreenContext = nextScreenContext ?: ScreenContext(Pasteboard)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (other !is ScreenContext) return false

        if (screenType != other.screenType) return false
        if (nextScreenContext != other.nextScreenContext) return false
        if (context != other.context) return false

        return true
    }

    override fun hashCode(): Int {
        var result = screenType.hashCode()
        result = 31 * result + (nextScreenContext?.hashCode() ?: 0)
        result = 31 * result + context.hashCode()
        return result
    }
}
