package com.crosspaste.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter

abstract class ImageData<T>(
    protected val key: Any,
    protected val imageData: T,
) : LoadStateData {

    abstract val isIcon: Boolean

    protected abstract fun loadPainter(imageData: T): Painter

    @Composable
    open fun readPainter(): Painter {
        return remember(key) { loadPainter(imageData) }
    }

    override fun getLoadState(): LoadState {
        return LoadState.Success
    }
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
