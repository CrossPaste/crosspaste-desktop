package com.clipevery.clip

import androidx.compose.runtime.State
import com.clipevery.app.AppWindowManager
import com.clipevery.dao.clip.ClipData

interface ClipSearchService {

    val selectedIndex: State<Int>

    val inputSearch: State<String>

    val searchResult: MutableList<ClipData>

    val currentClipData: State<ClipData?>

    val appWindowManager: AppWindowManager

    fun updateInputSearch(inputSearch: String)

    suspend fun search()

    fun tryStart(): Boolean

    fun stop()

    fun setSelectedIndex(selectedIndex: Int)

    fun upSelectedIndex()

    fun downSelectedIndex()

    suspend fun activeWindow()

    suspend fun unActiveWindow()

    suspend fun toPaste()
}
