package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import io.realm.kotlin.MutableRealm
import java.net.MalformedURLException
import java.net.URL

object GenerateUrlPlugin: ClipPlugin {

    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>, realm: MutableRealm): List<ClipAppearItem> {
        if (clipAppearItems.all { it !is UrlClipItem }) {
            clipAppearItems.filterIsInstance<TextClipItem>().firstOrNull()?.let {
                if (isUrl(it.text)) {
                    return clipAppearItems + UrlClipItem().apply {
                        this.identifier = it.identifier
                        this.url = it.text
                        this.md5 = it.md5
                    }
                }
            }
        }
        return clipAppearItems
    }

    private fun isUrl(string: String): Boolean {
        try {
            URL(string)
            return true
        } catch (e: MalformedURLException) {
            return false
        }
    }
}