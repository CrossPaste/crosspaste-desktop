package com.clipevery.clip.plugin

import com.clipevery.clip.ClipPlugin
import com.clipevery.clip.item.TextClipItem
import com.clipevery.clip.item.UrlClipItem
import com.clipevery.dao.clip.ClipAppearItem
import java.net.MalformedURLException
import java.net.URL

object ConvertUrlPlugin: ClipPlugin {
    override fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem> {
        return clipAppearItems.map {
            if (it is TextClipItem && isUrl(it.text)) {
                UrlClipItem().apply {
                    this.identifier = it.identifier
                    this.url = it.text
                    this.md5 = it.md5
                }
            } else {
                it
            }
        }
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