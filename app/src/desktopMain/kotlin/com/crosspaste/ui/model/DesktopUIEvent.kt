package com.crosspaste.ui.model

/** Asks the view owning [zone] to take keyboard focus; its focus events then update the focused zone. */
data class RequestFocus(
    val zone: FocusedElement,
) : UIEvent

object RequestCreateTag : UIEvent

object RequestOpenSettings : UIEvent
