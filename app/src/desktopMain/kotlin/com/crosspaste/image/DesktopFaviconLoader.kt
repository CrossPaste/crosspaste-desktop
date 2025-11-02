package com.crosspaste.image

import com.crosspaste.net.ResourcesClient
import com.crosspaste.path.UserDataPathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.jvm.javaio.*
import okio.Path
import java.io.FileOutputStream

class DesktopFaviconLoader(
    private val resourcesClient: ResourcesClient,
    userDataPathProvider: UserDataPathProvider,
) : AbstractFaviconLoader(userDataPathProvider) {

    override val logger = KotlinLogging.logger {}

    override suspend fun saveIco(
        url: String,
        path: Path,
    ): Path? =
        resourcesClient.request(url).getOrNull()?.let { response ->
            FileOutputStream(path.toFile()).use { output ->
                response.getBody().toInputStream().copyTo(output)
            }
            path
        }
}
