package com.crosspaste.app

import com.crosspaste.net.ResourcesClient
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class DesktopPromoteService(
    private val appUrls: AppUrls,
    private val resourcesClient: ResourcesClient,
) : PromoteService {

    private val logger = KotlinLogging.logger {}

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private val coroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _config: MutableStateFlow<PromoteConfig> =
        MutableStateFlow(PromoteConfig())

    override val config: StateFlow<PromoteConfig> = _config

    private var fetchJob: Job? = null

    override fun start() {
        fetchJob =
            coroutineScope.launch {
                fetchConfig()
            }
    }

    override fun stop() {
        fetchJob?.cancel()
    }

    private suspend fun fetchConfig() {
        runCatching {
            resourcesClient
                .request(appUrls.promoteUrl)
                .getOrNull()
                ?.let { response ->
                    val jsonString =
                        response.getBody().toInputStream().use { inputStream ->
                            inputStream.bufferedReader().readText()
                        }
                    _config.value = json.decodeFromString<PromoteConfig>(jsonString)
                    logger.info { "Promote config loaded successfully" }
                }
        }.onFailure { e ->
            logger.warn { "Failed to fetch promote config: ${e.message}" }
        }
    }
}
