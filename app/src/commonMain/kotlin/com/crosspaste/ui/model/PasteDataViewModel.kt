package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import com.crosspaste.paste.PasteData
import kotlinx.coroutines.flow.StateFlow

abstract class PasteDataViewModel : ViewModel() {

    abstract val pasteDataList: StateFlow<List<PasteData>>

    abstract fun loadMore()

    abstract fun pause()

    abstract fun resume()

    abstract fun cleanup()

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
