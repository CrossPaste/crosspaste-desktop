package com.crosspaste.image

import com.crosspaste.net.DesktopClient
import com.crosspaste.path.UserDataPathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.io.FileOutputStream

class DesktopFaviconLoader(
    userDataPathProvider: UserDataPathProvider,
) : AbstractFaviconLoader(userDataPathProvider) {

    override val logger = KotlinLogging.logger {}

    override fun saveIco(
        url: String,
        path: Path,
    ): Path? =
        DesktopClient.request(url) { response ->
            FileOutputStream(path.toFile()).use { output ->
                response.body().use { input ->
                    input.copyTo(output)
                }
            }
            path
        }
}
