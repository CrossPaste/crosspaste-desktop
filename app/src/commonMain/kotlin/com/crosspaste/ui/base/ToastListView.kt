package com.crosspaste.ui.base

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.notification.ToastManager
import com.crosspaste.ui.theme.AppUISize.tiny
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ToastListView(
    intOffset: IntOffset,
    enter: EnterTransition,
    exit: ExitTransition,
) {
    val toastManager = koinInject<ToastManager>()

    val toastList by toastManager.toastList.collectAsState()

    // Track toasts that are currently animating in the UI layer
    val animatingToasts = remember { mutableStateMapOf<Int, Boolean>() }

    // Create coroutine scope to handle animation and delay logic
    val coroutineScope = rememberCoroutineScope()

    Popup(
        alignment = Alignment.TopCenter,
        offset = intOffset,
        properties = PopupProperties(clippingEnabled = false),
    ) {
        Column(
            modifier = Modifier.wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Process current items in the toast list
            toastList.forEachIndexed { index, toast ->
                key(toast.messageId) {
                    // Mark this toast as currently animating
                    animatingToasts[toast.messageId] = true

                    // Create a transition state associated with animation
                    val visibleState = remember { MutableTransitionState(false) }
                    // Capture the latest reference to the removeToast function
                    val currentRemoveToast by rememberUpdatedState(toastManager::removeToast)

                    // Initial display of toast
                    LaunchedEffect(Unit) {
                        visibleState.targetState = true
                    }

                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter = enter,
                        exit = exit,
                    ) {
                        Box {
                            ToastView(
                                toast = toast,
                                onCancelTapped = {
                                    // Trigger exit animation first
                                    visibleState.targetState = false
                                    // Use coroutine scope to handle delay logic
                                    coroutineScope.launch {
                                        delay(300)
                                        currentRemoveToast(toast.messageId)
                                        animatingToasts.remove(toast.messageId)
                                    }
                                },
                            )
                        }
                    }

                    // If this toast has an auto-dismiss duration
                    // trigger the same animation after delay
                    toast.duration?.let { duration ->
                        LaunchedEffect(toast.messageId) {
                            delay(duration)
                            // Check if the toast is still in the list
                            if (animatingToasts.containsKey(toast.messageId)) {
                                // Trigger exit animation first
                                visibleState.targetState = false
                                // Remove data later
                                coroutineScope.launch {
                                    delay(300)
                                    currentRemoveToast(toast.messageId)
                                    animatingToasts.remove(toast.messageId)
                                }
                            }
                        }
                    }

                    if (index < toastList.size - 1) {
                        Spacer(modifier = Modifier.height(tiny))
                    }

                    // Cleanup
                    DisposableEffect(Unit) {
                        onDispose {
                            animatingToasts.remove(toast.messageId)
                        }
                    }
                }
            }
        }
    }
}
