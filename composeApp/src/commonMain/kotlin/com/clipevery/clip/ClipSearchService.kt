package com.clipevery.clip

import com.clipevery.app.AppUI

interface ClipSearchService {

    fun tryStart(): Boolean

    fun stop()

    fun getAppUI(): AppUI
}