package com.crosspaste.ui.model

/** The search window zone holding keyboard focus; [NONE] when focus sits elsewhere (e.g. a tag name editor). */
enum class FocusedElement {
    SEARCH_INPUT,
    TAG_BAR,
    PASTE_LIST,
    NONE,
}
