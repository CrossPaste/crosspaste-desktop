package com.crosspaste.ui.paste.preview

import androidx.compose.runtime.Composable
import com.crosspaste.paste.PasteSingleProcess

@Composable
expect fun PasteShimmer(singleProcess: PasteSingleProcess?)
