package com.crosspaste.ui.model

import androidx.lifecycle.viewModelScope
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class GeneralPasteSearchViewModel(
    private val pasteDao: PasteDao,
) : PasteSearchViewModel() {

    private val logger = KotlinLogging.logger {}

    @OptIn(ExperimentalCoroutinesApi::class)
    override val searchResults: StateFlow<List<PasteData>> =
        searchParams
            .distinctUntilChanged()
            .flatMapLatest { params ->
                logger.info { "to searchPasteDataFlow" }
                pasteDao.searchPasteDataFlow(
                    searchTerms = params.searchTerms,
                    favorite = if (params.favorite) true else null,
                    sort = params.sort,
                    pasteType = params.pasteType,
                    limit = params.limit,
                ).map { pasteDataList ->
                    pasteDataList.filter { it.isValid() }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = listOf(),
            )
}
