package com.crosspaste.ui.base

class LoadImageData(
    val key: Any,
    val toPainterImage: ToPainterImage,
) : LoadStateData {

    override fun getLoadState(): LoadState {
        return LoadState.Success
    }
}

class LoadIconData(
    val key: Any,
    val toPainterImage: ToPainterImage,
) : LoadStateData {

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
