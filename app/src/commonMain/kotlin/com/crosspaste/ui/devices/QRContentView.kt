package com.crosspaste.ui.devices

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppTokenApi
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.QRCodeGenerator
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xLargeRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun QRContentView() {
    val appTokenApi = koinInject<AppTokenApi>()
    val copywriter = koinInject<GlobalCopywriter>()
    val qrCodeGenerator = koinInject<QRCodeGenerator>()

    val appSizeValue = LocalAppSizeValueState.current
    val density = LocalDensity.current

    val width = with(density) { appSizeValue.qrCodeSize.width.roundToPx() }
    val height = with(density) { appSizeValue.qrCodeSize.height.roundToPx() }

    var qrImage: ImageBitmap? by remember { mutableStateOf(null) }

    val token by appTokenApi.token.collectAsState()

    val refreshProgress by appTokenApi.refreshProgress.collectAsState()

    LaunchedEffect(token) {
        // maybe slow (get host), we use ioDispatcher to avoid blocking the UI
        qrImage =
            withContext(ioDispatcher) {
                qrCodeGenerator
                    .generateQRCode(token)
                    .toImage(width, height) as ImageBitmap
            }
    }

    DisposableEffect(Unit) {
        appTokenApi.startRefresh(showToken = false)
        onDispose {
            appTokenApi.stopRefresh(hideToken = false)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(xLargeRoundedCornerShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = xxLarge, vertical = medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier =
                Modifier
                    .size(xxxxLarge)
                    .clip(mediumRoundedCornerShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(xxLarge),
            )
        }

        Spacer(modifier = Modifier.height(medium))

        Text(
            text = copywriter.getText("qr_scan_title"),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(tiny))

        Text(
            text = copywriter.getText("qr_scan_instruction"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Box(
            modifier =
                Modifier
                    .padding(vertical = xLarge)
                    .shadow(tiny3X, xLargeRoundedCornerShape)
                    .border(
                        width = tiny5X,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = xLargeRoundedCornerShape,
                    ).background(Color.White)
                    .padding(medium),
            contentAlignment = Alignment.Center,
        ) {
            qrImage?.let {
                Image(
                    modifier =
                        Modifier
                            .size(appSizeValue.qrCodeSize)
                            .clip(RoundedCornerShape(small2X)),
                    bitmap = it,
                    contentDescription = "QR Code",
                )
            } ?: run {
                LoadingSpinner(size = appSizeValue.qrCodeSize.width)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tiny3X),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(medium),
                    )
                    Spacer(modifier = Modifier.height(tiny3X))
                    Text(
                        text = copywriter.getText("qr_security_protection"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(medium))

        Text(
            text = copywriter.getText("qr_expiry_info", ((1 - refreshProgress) * 30).roundToInt().toString()),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
            color = MaterialTheme.colorScheme.outline,
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun LoadingSpinner(size: Dp) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )

    Icon(
        modifier =
            Modifier
                .size(size)
                .graphicsLayer(rotationZ = rotation),
        imageVector = Icons.Default.Autorenew,
        contentDescription = "Loading",
        tint = MaterialTheme.colorScheme.primary,
    )
}
