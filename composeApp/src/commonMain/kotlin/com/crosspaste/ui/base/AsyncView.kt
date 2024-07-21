package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.crosspaste.image.ErrorStateData
import com.crosspaste.image.LoadStateData
import com.crosspaste.image.LoadingStateData
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext

@Composable
fun AsyncView(
    key: Any,
    defaultValue: LoadStateData = LoadingStateData,
    load: suspend () -> LoadStateData,
    loadFor: @Composable (LoadStateData) -> Unit,
) {
    val state: LoadStateData by pasteProduceState(defaultValue, key) {
        value =
            withContext(ioDispatcher) {
                try {
                    load()
                } catch (e: Throwable) {
                    ErrorStateData(e)
                }
            }
    }

    loadFor(state)
}
