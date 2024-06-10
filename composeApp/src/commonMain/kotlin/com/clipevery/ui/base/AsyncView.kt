package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.clipevery.utils.ioDispatcher
import kotlinx.coroutines.withContext

@Composable
fun AsyncView(
    key: Any,
    defaultValue: LoadStateData = LoadingStateData,
    load: suspend () -> LoadStateData,
    loadFor: @Composable (LoadStateData) -> Unit,
) {
    val state: LoadStateData by clipProduceState(defaultValue, key) {
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
