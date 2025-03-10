package com.crosspaste.ui.base

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.notification.ToastManager
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun ToastListView() {
    val density = LocalDensity.current
    val toastManager = koinInject<ToastManager>()

    val toastList by toastManager.toastList.collectAsState()
    // Track Toasts that are currently animating, used to delay the actual removal
    val animatingToasts = remember { mutableStateMapOf<Int, Boolean>() }
    // Track Toasts that are about to be removed, used to trigger exit animations
    val toastsToRemove = remember { mutableStateMapOf<Int, Boolean>() }

    // Monitor changes in the Toast list
    LaunchedEffect(toastList) {
        // Check which Toasts have been removed (exist in animatingToasts but not in toastList)
        val toastIds = toastList.map { it.messageId }
        val toRemove = animatingToasts.keys.filter { !toastIds.contains(it) }

        toRemove.forEach { messageId ->
            // Mark this Toast for removal (which will trigger the exit animation)
            toastsToRemove[messageId] = true

            // After a delay, remove it from our tracking maps
            delay(300) // Allow enough time for the exit animation
            animatingToasts.remove(messageId)
            toastsToRemove.remove(messageId)
        }
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset =
            IntOffset(
                with(density) { (0.dp).roundToPx() },
                with(density) { (50.dp).roundToPx() },
            ),
        properties = PopupProperties(clippingEnabled = false),
    ) {
        Column(
            modifier = Modifier.wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Process items currently in the Toast list
            toastList.forEachIndexed { index, toast ->
                key(toast.messageId) {
                    // Track this Toast's animation state
                    animatingToasts[toast.messageId] = true

                    val visibleState = remember { MutableTransitionState(false) }
                    visibleState.targetState = true

                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter =
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            ) +
                                fadeIn(
                                    initialAlpha = 0f,
                                    animationSpec = tween(150),
                                ),
                        exit =
                            fadeOut(
                                animationSpec = tween(300),
                            ) +
                                shrinkVertically(
                                    animationSpec = tween(300),
                                ),
                    ) {
                        Box {
                            ToastView(
                                toast = toast,
                                onCancelTapped = {
                                    toastManager.removeToast(toast.messageId)
                                },
                            )
                        }
                    }

                    if (index < toastList.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Process items that are in exit animation
            // (removed from Toast list but still need to show exit animation)
            toastsToRemove.forEach { (messageId, _) ->
                // Skip if we don't have animation state for this Toast
                if (!animatingToasts.containsKey(messageId)) return@forEach

                key("removing-$messageId") {
                    // Create an animation state to control the exit
                    val visibleState = remember { MutableTransitionState(true) }

                    // Set to invisible to trigger the exit animation
                    LaunchedEffect(Unit) {
                        visibleState.targetState = false
                    }

                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter =
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(300),
                            ) +
                                fadeIn(
                                    initialAlpha = 0f,
                                    animationSpec = tween(150),
                                ),
                        exit =
                            fadeOut(
                                animationSpec = tween(300),
                            ) +
                                shrinkVertically(
                                    animationSpec = tween(300),
                                ),
                    ) {
                        // We can't actually see the content of this Toast anymore
                        // But we need a placeholder to trigger the animation
                        Box(modifier = Modifier.height(48.dp))
                    }

                    if (toastsToRemove.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
