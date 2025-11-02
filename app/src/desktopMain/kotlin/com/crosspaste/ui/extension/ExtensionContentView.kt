package com.crosspaste.ui.extension

import androidx.compose.runtime.Composable
import com.crosspaste.ui.base.ExpandViewProvider
import org.koin.compose.koinInject

@Composable
fun ExtensionContentView() {
    val expandViewProvider = koinInject<ExpandViewProvider>()

    expandViewProvider.ExpandView(
        barContent = {
            expandViewProvider.ExpandBarView(
                state = this.state,
                title = "proxy",
            )
        },
    ) {
        ProxyView()
    }
}
