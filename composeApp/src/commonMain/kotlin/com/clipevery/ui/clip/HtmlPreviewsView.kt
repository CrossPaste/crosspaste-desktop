package com.clipevery.ui.clip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipHtml
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.feed
import com.clipevery.ui.base.html
import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.jsbridge.dataToJsonString
import com.multiplatform.webview.jsbridge.processParams
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.util.KLogSeverity
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewStateWithHTMLData
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

val logger = KotlinLogging.logger {}

@Composable
fun HtmlPreviewView(clipData: ClipData) {
    clipData.getClipItem(ClipHtml::class)?.let {

        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()

        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(8.dp)
        ) {
            val webViewState = rememberWebViewStateWithHTMLData(
                data = it.html
            )
            val webViewNavigator: WebViewNavigator = rememberWebViewNavigator()
            val jsBridge = rememberWebViewJsBridge(webViewNavigator)

            LaunchedEffect(Unit) {
                initWebView(webViewState)
                initJsBridge(jsBridge)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    WebView(
                        modifier = Modifier.fillMaxSize(),
                        state = webViewState,
                        navigator = webViewNavigator,
                        webViewJsBridge = jsBridge,
                    )
                }

                Row(
                    modifier = Modifier.wrapContentWidth()
                        .weight(1f)
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        html(),
                        contentDescription = "Html",
                        modifier = Modifier.padding(3.dp).size(14.dp),
                        tint = MaterialTheme.colors.onBackground
                    )

                    Text(
                        modifier = Modifier.weight(1f),
                        text = copywriter.getText("Html"),
                        fontFamily = FontFamily.SansSerif,
                        style = TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

    }
}

fun initWebView(webViewState: WebViewState) {
    webViewState.webSettings.apply {
        zoomLevel = 1.0
        isJavaScriptEnabled = true
        logSeverity = KLogSeverity.Debug
        allowFileAccessFromFileURLs = true
        allowUniversalAccessFromFileURLs = true
        desktopWebSettings.apply {
            transparent = false
            disablePopupWindows = true
        }
    }
}

suspend fun initJsBridge(webViewJsBridge: WebViewJsBridge) {
    webViewJsBridge.register(HoverScrollJsMessageHandler())
    FlowEventBus.events.filter { it is NavigationEvent }.collect {
        logger.info {
            "Received NavigationEvent"
        }
    }
}

class HoverScrollJsMessageHandler : IJsMessageHandler {


    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        logger.info {
            "Received message: $message"
        }
        val param = processParams<HoverScrollModel>(message)
        val data = HoverScrollModel("KMM Received ${param.message}")
        callback(dataToJsonString(data))
        navigator?.coroutineScope?.launch {
            FlowEventBus.publishEvent(NavigationEvent())
        }
    }

    override fun methodName(): String {
        return "hoverScroll"
    }
}

@Serializable
data class HoverScrollModel(val message: String)

object FlowEventBus {
    private val mEvents = MutableSharedFlow<IEvent>()
    val events = mEvents.asSharedFlow()

    suspend fun publishEvent(event: IEvent) {
        mEvents.emit(event)
    }
}

interface IEvent

class NavigationEvent : IEvent
