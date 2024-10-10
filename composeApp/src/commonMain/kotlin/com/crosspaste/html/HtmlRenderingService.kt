package com.crosspaste.html

import okio.Path

interface HtmlRenderingService {

    var startSuccess: Boolean

    fun saveRenderImage(
        html: String,
        savePath: Path,
    )

    fun quit()
}
