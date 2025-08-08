package com.crosspaste.ui.model

import com.crosspaste.paste.PasteData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingPasteDataViewModel(
    marketingPasteData: MarketingPasteData,
) : PasteDataViewModel() {

    override val pasteDataList: StateFlow<List<PasteData>> =
        MutableStateFlow(marketingPasteData.getPasteDataList())

    override fun loadMore() {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun cleanup() {
    }
}
