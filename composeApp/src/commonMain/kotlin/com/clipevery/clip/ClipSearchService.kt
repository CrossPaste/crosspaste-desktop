package com.clipevery.clip

import androidx.compose.runtime.State
import com.clipevery.app.AppWindowManager
import com.clipevery.dao.clip.ClipData

interface ClipSearchService {

    val selectedIndex: State<Int>

    val searchResult: MutableList<ClipData>

    val currentClipData: State<ClipData?>

    val appWindowManager: AppWindowManager

    fun tryStart(): Boolean

    fun stop()

    fun setSelectedIndex(selectedIndex: Int)

    fun updateSearchResult(searchResult: List<ClipData>)

    fun upSelectedIndex()

    fun downSelectedIndex()

    fun activeWindow()

    suspend fun unActiveWindow()

    suspend fun toPaste()
}
