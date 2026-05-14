package com.crosspaste.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

// Network-discovery health is currently a Windows-only concern (firewall rules,
// "Network Discovery" toggle, public-vs-private profile). Mobile platforms
// supply trivial actuals — a State that always returns false and a no-op notice.

@Composable
expect fun rememberNetworkDiscoveryBlocked(): State<Boolean>

@Composable
expect fun NetworkDiscoveryBlockedNotice()
