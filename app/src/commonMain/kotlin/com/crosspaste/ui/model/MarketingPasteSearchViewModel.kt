package com.crosspaste.ui.model

import com.crosspaste.db.paste.PasteData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MarketingPasteSearchViewModel(
    marketingPasteData: MarketingPasteData,
) : PasteSearchViewModel() {

    override val searchResults: StateFlow<List<PasteData>> =
        MutableStateFlow(marketingPasteData.getPasteDataList())
}
