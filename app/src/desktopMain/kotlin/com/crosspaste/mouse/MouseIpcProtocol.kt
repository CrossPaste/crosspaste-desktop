package com.crosspaste.mouse

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

object MouseIpcProtocol {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "__unused__" // overridden per hierarchy via @JsonClassDiscriminator
        }
}

@Serializable
data class Position(
    val x: Int,
    val y: Int,
)

@Serializable
data class ScreenInfo(
    val id: Int,
    val width: Int,
    val height: Int,
    val x: Int,
    val y: Int,
    @SerialName("scale_factor") val scaleFactor: Double,
    @SerialName("is_primary") val isPrimary: Boolean,
)

@Serializable
data class IpcPeer(
    val name: String,
    val address: String,
    val position: Position,
    @SerialName("device_id") val deviceId: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("cmd")
sealed class IpcCommand {

    @Serializable
    @SerialName("start")
    data class Start(
        val port: Int,
        val peers: List<IpcPeer>,
    ) : IpcCommand()

    @Serializable
    @SerialName("stop")
    object Stop : IpcCommand()

    @Serializable
    @SerialName("update_layout")
    data class UpdateLayout(
        val peers: List<IpcPeer>,
    ) : IpcCommand()

    @Serializable
    @SerialName("get_status")
    object GetStatus : IpcCommand()

    @Serializable
    @SerialName("enumerate_local_screens")
    object EnumerateLocalScreens : IpcCommand()

    @Serializable
    @SerialName("get_capabilities")
    object GetCapabilities : IpcCommand()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("event")
sealed class IpcEvent {

    @Serializable
    @SerialName("initialized")
    data class Initialized(
        val screens: List<ScreenInfo>,
        @SerialName("protocol_version") val protocolVersion: Int,
    ) : IpcEvent()

    @Serializable
    @SerialName("ready")
    data class Ready(
        val screens: List<ScreenInfo>,
        val port: Int,
    ) : IpcEvent()

    @Serializable
    @SerialName("session_started")
    data class SessionStarted(
        val port: Int,
        @SerialName("local_device_id") val localDeviceId: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("peer_connected")
    data class PeerConnected(
        val name: String,
        @SerialName("device_id") val deviceId: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("peer_screens_learned")
    data class PeerScreensLearned(
        @SerialName("device_id") val deviceId: String,
        val screens: List<ScreenInfo>,
    ) : IpcEvent()

    @Serializable
    @SerialName("peer_disconnected")
    data class PeerDisconnected(
        val name: String,
        val reason: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("mode_changed")
    data class ModeChanged(
        val mode: String,
        val target: String? = null,
    ) : IpcEvent()

    @Serializable
    @SerialName("status")
    data class Status(
        val running: Boolean,
        val mode: String,
        @SerialName("connected_peers") val connectedPeers: List<String>,
    ) : IpcEvent()

    @Serializable
    @SerialName("local_screens")
    data class LocalScreens(
        val screens: List<ScreenInfo>,
    ) : IpcEvent()

    @Serializable
    @SerialName("capabilities")
    data class Capabilities(
        @SerialName("protocol_version") val protocolVersion: Int,
        val features: List<String>,
    ) : IpcEvent()

    @Serializable
    @SerialName("warning")
    data class Warning(
        val code: String,
        val message: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("license_status")
    data class LicenseStatus(
        val status: String,
        val message: String,
        @SerialName("trial_remaining_secs") val trialRemainingSecs: Long? = null,
    ) : IpcEvent()

    @Serializable
    @SerialName("error")
    data class Error(
        val message: String,
    ) : IpcEvent()

    @Serializable
    @SerialName("stopped")
    object Stopped : IpcEvent()
}
