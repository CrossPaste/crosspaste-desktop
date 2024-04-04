package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.clipevery.utils.ioDispatcher
import kotlinx.coroutines.withContext

@Composable
fun AsyncView(
    load: suspend () -> LoadStateData,
    loadFor: @Composable (LoadStateData) -> Unit) {
    val state: LoadStateData by produceState(LoadingStateData as LoadStateData) {
        value = withContext(ioDispatcher) {
            try {
                load()
            } catch (e: Throwable) {
                e.printStackTrace()
                ErrorStateData(e)
            }
        }
    }

    loadFor(state)
}

object LoadingStateData: LoadStateData {
    override fun getLoadState(): LoadState {
        return LoadState.Loading
    }
}

class ErrorStateData(val throwable: Throwable): LoadStateData {
    override fun getLoadState(): LoadState {
        return LoadState.Error
    }
}

interface LoadStateData {

    fun getLoadState(): LoadState

    fun isLoading(): Boolean {
        return getLoadState() == LoadState.Loading
    }

    fun isSuccess(): Boolean {
        return getLoadState() == LoadState.Success
    }

    fun isError(): Boolean {
        return getLoadState() == LoadState.Error
    }
}

enum class LoadState {
    Loading,
    Success,
    Error
}