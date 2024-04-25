package com.clipevery.clip

import androidx.compose.runtime.State
import com.clipevery.app.AppUI
import com.clipevery.dao.clip.ClipData

interface ClipSearchService {

    val selectedIndex: State<Int>

    val searchResult: MutableList<ClipData>

    val currentClipData: State<ClipData?>

    fun tryStart(): Boolean

    fun stop()

    fun getAppUI(): AppUI

    fun setSelectedIndex(selectedIndex: Int)

    fun updateSearchResult(searchResult: List<ClipData>)

    fun upSelectedIndex()

    fun downSelectedIndex()

    fun activeWindow()

    fun unActiveWindow()
}
