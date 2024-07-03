package com.crosspaste.clip.plugin

import com.crosspaste.clip.ClipPlugin
import com.crosspaste.clip.item.TextClipItem
import com.crosspaste.clip.item.UrlClipItem
import com.crosspaste.dao.clip.ClipItem
import io.realm.kotlin.MutableRealm
import java.net.MalformedURLException
import java.net.URL

object GenerateUrlPlugin : ClipPlugin {

    override fun pluginProcess(
        clipItems: List<ClipItem>,
        realm: MutableRealm,
    ): List<ClipItem> {
        if (clipItems.all { it !is UrlClipItem }) {
            clipItems.filterIsInstance<TextClipItem>().firstOrNull()?.let {
                if (isUrl(it.text)) {
                    return clipItems +
                        UrlClipItem().apply {
                            this.identifier = it.identifier
                            this.url = it.text
                            this.size = it.size
                            this.md5 = it.md5
                        }
                }
            }
        }
        return clipItems
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
