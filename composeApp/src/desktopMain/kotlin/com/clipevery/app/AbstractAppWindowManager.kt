package com.clipevery.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.clipevery.path.DesktopPathProvider
import com.clipevery.path.PathProvider
import com.clipevery.utils.Memoize
import com.clipevery.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.awt.Rectangle

abstract class AbstractAppWindowManager : AppWindowManager {

    companion object {
        const val MAIN_WINDOW_TITLE: String = "Clipevery"

        const val SEARCH_WINDOW_TITLE: String = "Clipevery Search"

        // only use in Windows
        const val MENU_WINDOW_TITLE: String = "Clipevery Menu"
    }

    protected val logger: KLogger = KotlinLogging.logger {}

    protected val ioScope = CoroutineScope(ioDispatcher + SupervisorJob())

    protected val pathProvider: PathProvider = DesktopPathProvider

    override var showMainWindow by mutableStateOf(false)

    override var mainWindowState: WindowState by mutableStateOf(
        WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.PlatformDefault,
            size = DpSize(width = 460.dp, height = 710.dp),
        ),
    )

    override var showMainDialog by mutableStateOf(false)

    override var showSearchWindow by mutableStateOf(false)

    override var searchWindowState: WindowState by mutableStateOf(
        WindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.Aligned(Alignment.Center),
            size = DpSize(width = 800.dp, height = 520.dp),
        ),
    )

    override var searchFocusRequester = FocusRequester()

    override val searchWindowDetailViewDpSize = DpSize(width = 500.dp, height = 240.dp)

    protected val calPosition: (Rectangle) -> WindowPosition =
        Memoize.memoize { bounds ->
            val windowSize = searchWindowState.size
            WindowPosition(
                x = (bounds.x.dp + ((bounds.width.dp - windowSize.width) / 2)),
                y = (bounds.y.dp + ((bounds.height.dp - windowSize.height) / 2)),
            )
        }
}
