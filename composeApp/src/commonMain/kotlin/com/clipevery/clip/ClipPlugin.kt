package com.clipevery.clip

import com.clipevery.dao.clip.ClipAppearItem

interface ClipPlugin {

    fun pluginProcess(clipAppearItems: List<ClipAppearItem>): List<ClipAppearItem>
}