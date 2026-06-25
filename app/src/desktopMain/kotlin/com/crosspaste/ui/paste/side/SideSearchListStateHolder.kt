package com.crosspaste.ui.paste.side

import androidx.compose.foundation.lazy.LazyListState

/**
 * UI-layer bridge for the side search list's [LazyListState].
 *
 * The state is created with `remember` inside the search window content (so it lives in the same
 * composition/ComposeScene as the `LazyRow` that scrolls it — hoisting it any higher, e.g. into the
 * application composition, makes scrolling janky). But two consumers live outside that composition
 * and need the same instance:
 *  - the always-composed `SearchWindow` body, which scrolls back to the top on reopen, and
 *  - `BubbleWindow`, a sibling window that reads item layout positions to place its tail.
 *
 * This holder is that bridge. It is deliberately a thin UI-layer object rather than a field on a
 * ViewModel: the ViewModel stays free of Compose objects and unit-testable, while the [LazyListState]
 * keeps its performance-critical composition locality.
 */
class SideSearchListStateHolder {
    var listState: LazyListState? = null
}
