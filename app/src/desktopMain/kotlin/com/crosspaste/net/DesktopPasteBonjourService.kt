package com.crosspaste.net

import com.crosspaste.app.AppInfo
import com.crosspaste.app.EndpointInfoFactory
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.utils.DesktopControlUtils.ensureMinExecutionTime
import com.crosspaste.utils.TxtRecordUtils
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.jmdns.ServiceInfo
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DesktopPasteBonjourService internal constructor(
    private val appInfo: AppInfo,
    private val endpointInfoFactory: EndpointInfoFactory,
    private val nearbyDeviceManager: NearbyDeviceManager,
    private val networkInterfaceService: NetworkInterfaceService,
    private val scope: CoroutineScope,
    private val jmdnsFactory: JmDnsFactory,
    private val cleanupDispatcher: CoroutineDispatcher = ioDispatcher,
) : PasteBonjourService {

    constructor(
        appInfo: AppInfo,
        endpointInfoFactory: EndpointInfoFactory,
        nearbyDeviceManager: NearbyDeviceManager,
        networkInterfaceService: NetworkInterfaceService,
    ) : this(
        appInfo,
        endpointInfoFactory,
        nearbyDeviceManager,
        networkInterfaceService,
        namedScope(ioDispatcher, "DesktopPasteBonjourService"),
        DefaultJmDnsFactory,
    )

    constructor(
        appInfo: AppInfo,
        endpointInfoFactory: EndpointInfoFactory,
        nearbyDeviceManager: NearbyDeviceManager,
        networkInterfaceService: NetworkInterfaceService,
        scope: CoroutineScope,
    ) : this(
        appInfo,
        endpointInfoFactory,
        nearbyDeviceManager,
        networkInterfaceService,
        scope,
        DefaultJmDnsFactory,
    )

    companion object {
        internal const val SERVICE_TYPE = "_crosspasteService._tcp.local."

        private const val ACTIVE_SCAN_TIMEOUT = 3000L
        private const val INTERFACE_SCAN_INTERVAL = 5000L
        private const val DEVICE_RESOLVE_INTERVAL = 2000L
        private const val MIN_SEARCH_DURATION = 2000L
        private const val CLOSE_TIMEOUT = 10000L
        private const val LIFECYCLE_CLOSE_TIMEOUT = 4000L
        private const val MAX_SETUP_RETRIES = 3

        private val SETUP_RETRY_DELAY = 2.seconds
        private val dateUtils = getDateUtils()
    }

    private data class ActiveJmDns(
        val id: Long,
        val generation: Long,
        val hostAddress: String,
        val instance: JmDnsInstance,
        val listener: DesktopServiceListener,
    )

    private data class DiscoverySource(
        val registrationId: Long,
        val serviceName: String,
    )

    private val logger = KotlinLogging.logger {}

    // A rebuild can take seconds. We only need to converge on the latest snapshot,
    // so collapse intermediate network-interface changes.
    private val actionChannel = Channel<List<NetworkInterfaceInfo>>(Channel.CONFLATED)

    private val stateLock = Any()
    private val registrations = mutableMapOf<String, ActiveJmDns>()
    private val presenceBySource = mutableMapOf<DiscoverySource, SyncInfo>()
    private val generation = AtomicLong(0)
    private val registrationId = AtomicLong(0)
    private val refreshAllRunning = AtomicBoolean(false)

    private val interfaceScanThrottle: MutableMap<String, Long> = ConcurrentMap()
    private val deviceResolveThrottle: MutableMap<String, Long> = ConcurrentMap()

    private var setupRetryJob: Job? = null

    init {
        scope.launch {
            networkInterfaceService.networkInterfaces.collect { interfaces ->
                actionChannel.send(interfaces)
            }
        }

        scope.launch {
            for (interfaces in actionChannel) {
                try {
                    processNetworkChange(interfaces)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Keep the sole rebuild consumer alive so a later network snapshot
                    // can recover from an unexpected setup failure.
                    logger.error(e) { "Failed to rebuild Bonjour services" }
                }
            }
        }
    }

    suspend fun processNetworkChange(interfaces: List<NetworkInterfaceInfo>) {
        setupRetryJob?.cancel()
        val currentGeneration = generation.incrementAndGet()
        closeServices(detachServices())

        if (interfaces.isNotEmpty()) {
            setup(interfaces, currentGeneration)
        }
    }

    private suspend fun setup(
        interfaces: List<NetworkInterfaceInfo>,
        currentGeneration: Long,
    ) {
        val hostInfoList = interfaces.map { info -> info.toHostInfo() }
        val endpointInfo = endpointInfoFactory.createEndpointInfo(hostInfoList)
        val syncInfo = SyncInfo(appInfo, endpointInfo)
        val txtRecordDict = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)

        logger.debug { "Registering service: $syncInfo" }

        val failed = setupInterfaces(interfaces, currentGeneration, endpointInfo, txtRecordDict)
        scheduleSetupRetry(failed, currentGeneration, endpointInfo, txtRecordDict)
    }

    private suspend fun setupInterfaces(
        interfaces: List<NetworkInterfaceInfo>,
        currentGeneration: Long,
        endpointInfo: EndpointInfo,
        txtRecordDict: Map<String, String>,
    ): List<NetworkInterfaceInfo> =
        supervisorScope {
            interfaces
                .map { networkInterface ->
                    async {
                        networkInterface.takeUnless {
                            setupInterface(it, currentGeneration, endpointInfo, txtRecordDict)
                        }
                    }
                }.awaitAll()
                .filterNotNull()
        }

    private fun setupInterface(
        networkInterface: NetworkInterfaceInfo,
        currentGeneration: Long,
        endpointInfo: EndpointInfo,
        txtRecordDict: Map<String, String>,
    ): Boolean {
        if (generation.get() != currentGeneration) return true

        val hostAddress = networkInterface.hostAddress
        var instance: JmDnsInstance? = null
        return try {
            instance = jmdnsFactory.create(InetAddress.getByName(hostAddress))
            instance.registerService(
                ServiceInfo.create(
                    SERVICE_TYPE,
                    "crosspaste@${appInfo.appInstanceId}@${hostAddress.replace(".", "_")}",
                    endpointInfo.port,
                    0,
                    0,
                    txtRecordDict,
                ),
            )

            val id = registrationId.incrementAndGet()
            val listener =
                DesktopServiceListener(
                    isActive = { isRegistrationActive(hostAddress, id, currentGeneration) },
                    onResolved = { serviceName, syncInfo ->
                        recordResolved(hostAddress, id, currentGeneration, serviceName, syncInfo)
                    },
                    onRemoved = { serviceName, appInstanceId ->
                        recordRemoved(hostAddress, id, currentGeneration, serviceName, appInstanceId)
                    },
                )
            val registration =
                ActiveJmDns(
                    id = id,
                    generation = currentGeneration,
                    hostAddress = hostAddress,
                    instance = instance,
                    listener = listener,
                )

            val activated =
                synchronized(stateLock) {
                    if (generation.get() != currentGeneration || registrations.containsKey(hostAddress)) {
                        false
                    } else {
                        registrations[hostAddress] = registration
                        try {
                            instance.addServiceListener(SERVICE_TYPE, listener)
                            true
                        } catch (e: Throwable) {
                            registrations.remove(hostAddress, registration)
                            throw e
                        }
                    }
                }

            if (!activated) {
                closeFailedInstance(instance)
            }
            true
        } catch (e: Throwable) {
            instance?.let(::closeFailedInstance)
            logger.warn(e) { "Failed to register Bonjour service on $hostAddress" }
            false
        }
    }

    private fun scheduleSetupRetry(
        initialFailures: List<NetworkInterfaceInfo>,
        currentGeneration: Long,
        endpointInfo: EndpointInfo,
        txtRecordDict: Map<String, String>,
    ) {
        if (initialFailures.isEmpty() || generation.get() != currentGeneration) return

        setupRetryJob =
            scope.launch {
                var failures = initialFailures
                repeat(MAX_SETUP_RETRIES) {
                    delay(SETUP_RETRY_DELAY)
                    if (generation.get() != currentGeneration) return@launch
                    failures = setupInterfaces(failures, currentGeneration, endpointInfo, txtRecordDict)
                    if (failures.isEmpty()) return@launch
                }
                logger.warn {
                    "Bonjour setup still failing on: ${failures.joinToString { it.hostAddress }}"
                }
            }
    }

    override fun refreshAll() {
        if (!refreshAllRunning.compareAndSet(false, true)) return

        scope.launch {
            try {
                nearbyDeviceManager.startSearching()
                ensureMinExecutionTime(MIN_SEARCH_DURATION) {
                    logger.info { "Manual refresh started..." }
                    supervisorScope {
                        activeRegistrations()
                            .map { registration ->
                                async { refreshAllOnInterface(registration) }
                            }.awaitAll()
                    }
                }.getOrThrow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Error during manual refreshAll" }
            } finally {
                nearbyDeviceManager.stopSearching()
                refreshAllRunning.set(false)
                logger.info { "Manual refresh completed." }
            }
        }
    }

    private fun refreshAllOnInterface(registration: ActiveJmDns) {
        if (!isRegistrationActive(registration)) return

        try {
            registration.instance.removeServiceListener(SERVICE_TYPE, registration.listener)
            registration.instance.addServiceListener(SERVICE_TYPE, registration.listener)

            val services = registration.instance.list(SERVICE_TYPE, ACTIVE_SCAN_TIMEOUT)
            logger.debug {
                "Interface ${registration.hostAddress} found ${services.size} services"
            }
            services.forEach { serviceInfo ->
                registration.instance.requestServiceInfo(SERVICE_TYPE, serviceInfo.name)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to refresh Bonjour interface ${registration.hostAddress}" }
        }
    }

    override fun refreshTarget(
        appInstanceId: String,
        hostInfoList: List<HostInfo>,
    ) {
        // mDNS visibility is determined by active local interfaces. hostInfoList remains
        // part of the shared API for discovery backends that can target remote addresses.
        scope.launch {
            val currentTime = dateUtils.nowEpochMilliseconds()
            activeRegistrations()
                .map { registration ->
                    async {
                        try {
                            refreshTargetOnInterface(registration, appInstanceId, currentTime)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            logger.warn(e) {
                                "Failed to refresh $appInstanceId on ${registration.hostAddress}"
                            }
                        }
                    }
                }.awaitAll()
        }
    }

    private fun refreshTargetOnInterface(
        registration: ActiveJmDns,
        appInstanceId: String,
        currentTime: Long,
    ) {
        if (!isRegistrationActive(registration)) return

        val servicePrefix = "crosspaste@$appInstanceId@"
        var targetServiceName = cachedServiceName(registration.id, appInstanceId)

        if (targetServiceName == null &&
            tryAcquire(
                interfaceScanThrottle,
                registration.hostAddress,
                currentTime,
                INTERFACE_SCAN_INTERVAL,
            )
        ) {
            logger.info {
                "Performing active scan for $appInstanceId on ${registration.hostAddress}"
            }
            targetServiceName =
                registration.instance
                    .list(SERVICE_TYPE, ACTIVE_SCAN_TIMEOUT)
                    .find { it.name.startsWith(servicePrefix) }
                    ?.name
        }

        val deviceKey = "${registration.hostAddress}_$appInstanceId"
        if (targetServiceName != null &&
            tryAcquire(deviceResolveThrottle, deviceKey, currentTime, DEVICE_RESOLVE_INTERVAL)
        ) {
            logger.info {
                "Requesting service info for $appInstanceId via ${registration.hostAddress}"
            }
            registration.instance.requestServiceInfo(SERVICE_TYPE, targetServiceName)
        }
    }

    private fun tryAcquire(
        throttle: MutableMap<String, Long>,
        key: String,
        currentTime: Long,
        interval: Long,
    ): Boolean {
        var acquired = false
        throttle.compute(key) { _, lastTime ->
            if (lastTime == null || currentTime - lastTime > interval) {
                acquired = true
                currentTime
            } else {
                lastTime
            }
        }
        return acquired
    }

    private fun recordResolved(
        hostAddress: String,
        id: Long,
        currentGeneration: Long,
        serviceName: String,
        syncInfo: SyncInfo,
    ) {
        synchronized(stateLock) {
            if (!isRegistrationActiveLocked(hostAddress, id, currentGeneration)) return

            val source = DiscoverySource(id, serviceName)
            val previous = presenceBySource.put(source, syncInfo)
            if (previous != null &&
                previous.appInfo.appInstanceId != syncInfo.appInfo.appInstanceId &&
                presenceBySource.values.none {
                    it.appInfo.appInstanceId == previous.appInfo.appInstanceId
                }
            ) {
                nearbyDeviceManager.removeDevice(previous.appInfo.appInstanceId)
            }
            nearbyDeviceManager.addDevice(syncInfo)
        }
    }

    private fun recordRemoved(
        hostAddress: String,
        id: Long,
        currentGeneration: Long,
        serviceName: String,
        appInstanceId: String,
    ) {
        synchronized(stateLock) {
            if (!isRegistrationActiveLocked(hostAddress, id, currentGeneration)) return

            val removed = presenceBySource.remove(DiscoverySource(id, serviceName))
            val removedAppInstanceId = removed?.appInfo?.appInstanceId ?: appInstanceId
            if (presenceBySource.values.none {
                    it.appInfo.appInstanceId == removedAppInstanceId
                }
            ) {
                nearbyDeviceManager.removeDevice(removedAppInstanceId)
            }
        }
    }

    private fun cachedServiceName(
        id: Long,
        appInstanceId: String,
    ): String? =
        synchronized(stateLock) {
            presenceBySource.entries
                .firstOrNull { (source, syncInfo) ->
                    source.registrationId == id &&
                        syncInfo.appInfo.appInstanceId == appInstanceId
                }?.key
                ?.serviceName
        }

    private fun isRegistrationActive(
        hostAddress: String,
        id: Long,
        currentGeneration: Long,
    ): Boolean =
        synchronized(stateLock) {
            isRegistrationActiveLocked(hostAddress, id, currentGeneration)
        }

    private fun isRegistrationActive(registration: ActiveJmDns): Boolean =
        isRegistrationActive(
            registration.hostAddress,
            registration.id,
            registration.generation,
        )

    private fun isRegistrationActiveLocked(
        hostAddress: String,
        id: Long,
        currentGeneration: Long,
    ): Boolean = generation.get() == currentGeneration && registrations[hostAddress]?.id == id

    private fun activeRegistrations(): List<ActiveJmDns> =
        synchronized(stateLock) {
            registrations.values.toList()
        }

    private fun detachServices(): List<ActiveJmDns> =
        synchronized(stateLock) {
            val detached = registrations.values.toList()
            registrations.clear()
            interfaceScanThrottle.clear()
            deviceResolveThrottle.clear()

            val appInstanceIds =
                presenceBySource.values
                    .map { it.appInfo.appInstanceId }
                    .distinct()
            presenceBySource.clear()
            appInstanceIds.forEach(nearbyDeviceManager::removeDevice)
            detached
        }

    private suspend fun closeServices(services: List<ActiveJmDns>) {
        if (services.isEmpty()) return

        // JmDNS close is blocking and ignores coroutine cancellation. Keep it in a
        // detached cleanup scope so the timeout bounds the rebuild caller's wait;
        // inactive instances that time out continue closing in the background.
        val cleanupScope = CoroutineScope(cleanupDispatcher + SupervisorJob())
        val cleanupJob =
            cleanupScope.launch {
                services
                    .map { registration ->
                        async {
                            runCatching { registration.instance.close() }
                                .onFailure { e ->
                                    logger.warn(e) {
                                        "Failed to close Bonjour service on ${registration.hostAddress}"
                                    }
                                }
                        }
                    }.awaitAll()
            }
        cleanupJob.invokeOnCompletion { cleanupScope.cancel() }

        val completed =
            withTimeoutOrNull(CLOSE_TIMEOUT.milliseconds) {
                cleanupJob.join()
                true
            } ?: false
        if (!completed) {
            logger.warn { "Bonjour service close exceeded ${CLOSE_TIMEOUT}ms" }
        }
    }

    private fun closeFailedInstance(instance: JmDnsInstance) {
        runCatching { instance.close() }
            .onFailure { e -> logger.warn(e) { "Failed to close incomplete Bonjour service" } }
    }

    override fun close() {
        scope.cancel()
        generation.incrementAndGet()
        val services = detachServices()

        val cleanupScope = CoroutineScope(cleanupDispatcher + SupervisorJob())
        val cleanupJob =
            cleanupScope.launch {
                closeServices(services)
            }
        cleanupJob.invokeOnCompletion { cleanupScope.cancel() }

        runBlocking {
            val completed =
                withTimeoutOrNull(LIFECYCLE_CLOSE_TIMEOUT.milliseconds) {
                    cleanupJob.join()
                    true
                } ?: false
            if (!completed) {
                logger.warn {
                    "Bonjour lifecycle cleanup exceeded ${LIFECYCLE_CLOSE_TIMEOUT}ms; continuing shutdown"
                }
            }
        }
    }
}
