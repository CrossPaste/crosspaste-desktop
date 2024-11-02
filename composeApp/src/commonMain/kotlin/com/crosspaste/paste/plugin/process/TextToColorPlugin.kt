package com.crosspaste.paste.plugin.process

import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.utils.getColorUtils
import io.realm.kotlin.MutableRealm

object TextToColorPlugin : PasteProcessPlugin {

    private val colorUtils = getColorUtils()

    override fun process(
        pasteItems: List<PasteItem>,
        realm: MutableRealm,
        source: String?,
    ): List<PasteItem> {
        if (pasteItems.all { it !is ColorPasteItem }) {
            pasteItems.filterIsInstance<TextPasteItem>().firstOrNull()?.let {
                colorUtils.tryCovertToColor(it.text)?.let { color ->
                    return pasteItems +
                        ColorPasteItem().apply {
                            this.identifier = it.identifier
                            this.color = color
                            this.size = 4L
                            this.hash = color.toString()
                        }
                }
            }
        }
        return pasteItems
    }
}
