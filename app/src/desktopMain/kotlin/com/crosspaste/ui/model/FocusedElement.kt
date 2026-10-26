package com.crosspaste.ui.model

/**
 * The search window zone holding keyboard focus, top bar left to right then the list;
 * [NONE] when focus sits elsewhere (e.g. a tag name editor).
 */
enum class FocusedElement {
    SEARCH_INPUT,
    TYPE_FILTER,
    TAG_BAR,
    SETTINGS,
    PASTE_LIST,
    NONE,
}
