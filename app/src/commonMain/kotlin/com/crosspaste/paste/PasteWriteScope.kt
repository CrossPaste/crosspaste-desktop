package com.crosspaste.paste

import com.crosspaste.config.AppConfig

/**
 * Which items of a paste are written to the clipboard when the user pastes it.
 *
 * A paste can carry several items — a file next to its preview image, or
 * representations from different categories. Items fall into two categories,
 * files and everything else (content), and a clipboard write either sticks to the
 * category of the primary item or hands out everything.
 */
enum class PasteWriteScope {
    /**
     * Only the items in the primary item's category. For example, a copied file is
     * written as a file without representations of the other category riding along.
     */
    PRIMARY_CATEGORY,

    /**
     * Every item, whatever its category, so the receiving app can pick whichever
     * representation it understands. Plugins may add extra flavors in this scope
     * that only make sense next to items of the other category.
     */
    ALL,
}

fun AppConfig.pasteWriteScope(): PasteWriteScope =
    if (pastePrimaryTypeOnly) PasteWriteScope.PRIMARY_CATEGORY else PasteWriteScope.ALL
