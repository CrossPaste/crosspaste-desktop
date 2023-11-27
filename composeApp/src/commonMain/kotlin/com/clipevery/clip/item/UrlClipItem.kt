package com.clipevery.clip.item

import com.clipevery.clip.ClipItemType
import java.net.URL

class UrlClipItem(override val url: URL): TextClipItem(url.toString()), ClipUrl {
    override val clipItemType: ClipItemType = ClipItemType.Url
}