package com.crosspaste.paste

import com.crosspaste.app.AppWindowManager
import com.crosspaste.dao.paste.PasteData

interface PasteSearchService {

    var selectedIndex: Int

    var inputSearch: String

    var searchFavorite: Boolean

    var searchSort: Boolean

    var searchPasteType: Int?

    var searchLimit: Int

    var searchTime: Int

    val searchResult: MutableList<PasteData>

    val currentPasteData: PasteData?

    val appWindowManager: AppWindowManager

    fun updateInputSearch(inputSearch: String)

    fun switchFavorite()

    fun switchSort()

    fun setPasteType(pasteType: Int?)

    fun tryAddLimit(): Boolean

    suspend fun search(keepSelectIndex: Boolean = false)

    fun clickSetSelectedIndex(selectedIndex: Int)

    fun upSelectedIndex()

    fun downSelectedIndex()

    suspend fun activeWindow()

    suspend fun unActiveWindow()

    suspend fun toPaste()
}
