package com.crosspaste.image

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.net.ResourceRequestLimits
import com.crosspaste.net.ResourcesClient
import com.crosspaste.path.UserDataPathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.jvm.javaio.*
import okio.Path
import java.io.FileOutputStream

class DesktopFaviconLoader(
    configManager: CommonConfigManager,
    private val resourcesClient: ResourcesClient,
    userDataPathProvider: UserDataPathProvider,
) : AbstractFaviconLoader(configManager, userDataPathProvider) {

    override val logger = KotlinLogging.logger {}

    override suspend fun saveIco(
        url: String,
        path: Path,
    ): Path? =
        resourcesClient.request(url, ResourceRequestLimits.IMAGE).getOrNull()?.let { response ->
            FileOutputStream(path.toFile()).use { output ->
                response.getBody().toInputStream().use { input ->
                    input.copyTo(output)
                }
            }
            path
        }
}
