package com.clipevery.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.clipevery.utils.ioDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ClipProduceStateScopeImpl<T>(
    state: MutableState<T>,
    override val coroutineContext: CoroutineContext,
) : ProduceStateScope<T>, MutableState<T> by state {

    override suspend fun awaitDispose(onDispose: () -> Unit): Nothing {
        try {
            suspendCancellableCoroutine<Nothing> { }
        } finally {
            onDispose()
        }
    }
}

@Composable
fun <T> clipProduceState(
    initialValue: T,
    key1: Any?,
    producer: suspend ProduceStateScope<T>.() -> Unit,
): State<T> {
    val result = remember(key1) { mutableStateOf(initialValue) }
    LaunchedEffect(key1) {
        ClipProduceStateScopeImpl(result, coroutineContext).producer()
    }
    return result
}

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

object LoadingStateData : LoadStateData {
    override fun getLoadState(): LoadState {
        return LoadState.Loading
    }
}

class ErrorStateData(val throwable: Throwable) : LoadStateData {
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
    Error,
}
