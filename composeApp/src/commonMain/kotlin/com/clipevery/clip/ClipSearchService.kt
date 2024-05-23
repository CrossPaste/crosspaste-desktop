package com.clipevery.clip

import androidx.compose.runtime.State
import com.clipevery.app.AppWindowManager
import com.clipevery.dao.clip.ClipData

interface ClipSearchService {

    var selectedIndex: Int

    var inputSearch: String

    var searchFavorite: Boolean

    var searchSort: Boolean

    var searchClipType: Int?

    var searchLimit: Int

    var searchTime: Int

    val searchResult: MutableList<ClipData>

    val currentClipData: State<ClipData?>

    val appWindowManager: AppWindowManager

    fun updateInputSearch(inputSearch: String)

    fun switchFavorite()

    fun switchSort()

    fun setClipType(clipType: Int?)

    fun tryAddLimit(): Boolean

    suspend fun search(keepSelectIndex: Boolean = false)

    fun clickSetSelectedIndex(selectedIndex: Int)

    fun upSelectedIndex()

    fun downSelectedIndex()

    suspend fun activeWindow()

    suspend fun unActiveWindow()

    suspend fun toPaste()
}
