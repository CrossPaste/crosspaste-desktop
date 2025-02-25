package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.app.generated.resources.Res
import com.crosspaste.app.generated.resources.add
import com.crosspaste.app.generated.resources.alert_circle
import com.crosspaste.app.generated.resources.archive
import com.crosspaste.app.generated.resources.arrow_back
import com.crosspaste.app.generated.resources.autorenew
import com.crosspaste.app.generated.resources.bell
import com.crosspaste.app.generated.resources.circle
import com.crosspaste.app.generated.resources.clipboard
import com.crosspaste.app.generated.resources.close
import com.crosspaste.app.generated.resources.contrast_high
import com.crosspaste.app.generated.resources.contrast_medium
import com.crosspaste.app.generated.resources.contrast_standard
import com.crosspaste.app.generated.resources.database
import com.crosspaste.app.generated.resources.debug
import com.crosspaste.app.generated.resources.edit
import com.crosspaste.app.generated.resources.favorite
import com.crosspaste.app.generated.resources.image_compress
import com.crosspaste.app.generated.resources.image_expand
import com.crosspaste.app.generated.resources.magnifying
import com.crosspaste.app.generated.resources.more_vertical
import com.crosspaste.app.generated.resources.no_favorite
import com.crosspaste.app.generated.resources.percent
import com.crosspaste.app.generated.resources.question
import com.crosspaste.app.generated.resources.refresh
import com.crosspaste.app.generated.resources.remove
import com.crosspaste.app.generated.resources.save
import com.crosspaste.app.generated.resources.scan
import com.crosspaste.app.generated.resources.search
import com.crosspaste.app.generated.resources.settings
import com.crosspaste.app.generated.resources.skip_forward
import com.crosspaste.app.generated.resources.sort_asc
import com.crosspaste.app.generated.resources.sort_desc
import com.crosspaste.app.generated.resources.to_top
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun add(): Painter {
    return painterResource(Res.drawable.add)
}

@Composable
actual fun alertCircle(): Painter {
    return painterResource(Res.drawable.alert_circle)
}

@Composable
actual fun archive(): Painter {
    return painterResource(Res.drawable.archive)
}

@Composable
actual fun arrowBack(): Painter {
    return painterResource(Res.drawable.arrow_back)
}

@Composable
actual fun ascSort(): Painter {
    return painterResource(Res.drawable.sort_asc)
}

@Composable
actual fun autoRenew(): Painter {
    return painterResource(Res.drawable.autorenew)
}

@Composable
actual fun bell(): Painter {
    return painterResource(Res.drawable.bell)
}

@Composable
actual fun circle(): Painter {
    return painterResource(Res.drawable.circle)
}

@Composable
actual fun clipboard(): Painter {
    return painterResource(Res.drawable.clipboard)
}

@Composable
actual fun close(): Painter {
    return painterResource(Res.drawable.close)
}

@Composable
actual fun contrastHigh(): Painter {
    return painterResource(Res.drawable.contrast_high)
}

@Composable
actual fun contrastMedium(): Painter {
    return painterResource(Res.drawable.contrast_medium)
}

@Composable
actual fun contrastStandard(): Painter {
    return painterResource(Res.drawable.contrast_standard)
}

@Composable
actual fun database(): Painter {
    return painterResource(Res.drawable.database)
}

@Composable
actual fun debug(): Painter {
    return painterResource(Res.drawable.debug)
}

@Composable
actual fun descSort(): Painter {
    return painterResource(Res.drawable.sort_desc)
}

@Composable
actual fun edit(): Painter {
    return painterResource(Res.drawable.edit)
}

@Composable
actual fun favorite(): Painter {
    return painterResource(Res.drawable.favorite)
}

@Composable
actual fun imageCompress(): Painter {
    return painterResource(Res.drawable.image_compress)
}

@Composable
actual fun imageExpand(): Painter {
    return painterResource(Res.drawable.image_expand)
}

@Composable
actual fun magnifying(): Painter {
    return painterResource(Res.drawable.magnifying)
}

@Composable
actual fun moreVertical(): Painter {
    return painterResource(Res.drawable.more_vertical)
}

@Composable
actual fun noFavorite(): Painter {
    return painterResource(Res.drawable.no_favorite)
}

@Composable
actual fun percent(): Painter {
    return painterResource(Res.drawable.percent)
}

@Composable
actual fun question(): Painter {
    return painterResource(Res.drawable.question)
}

@Composable
actual fun refresh(): Painter {
    return painterResource(Res.drawable.refresh)
}

@Composable
actual fun remove(): Painter {
    return painterResource(Res.drawable.remove)
}

@Composable
actual fun save(): Painter {
    return painterResource(Res.drawable.save)
}

@Composable
actual fun scan(): Painter {
    return painterResource(Res.drawable.scan)
}

@Composable
actual fun search(): Painter {
    return painterResource(Res.drawable.search)
}

@Composable
actual fun settings(): Painter {
    return painterResource(Res.drawable.settings)
}

@Composable
actual fun skipForward(): Painter {
    return painterResource(Res.drawable.skip_forward)
}

@Composable
actual fun toTop(): Painter {
    return painterResource(Res.drawable.to_top)
}
