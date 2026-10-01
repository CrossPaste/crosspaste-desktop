package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class DeviceSummary(
    val appInstanceId: String,
    val deviceName: String,
    val noteName: String?,
    val platform: String,
    val appVersion: String,
    val connectState: Int,
    val connectHostAddress: String?,
    val port: Int,
    val allowSend: Boolean,
    val allowReceive: Boolean,
)

class DevicesCommand : CliktCommand(name = "devices") {

    override fun help(context: Context): String = "List paired devices, or remove / block one"

    override val invokeWithoutSubcommand = true

    private val ctx by requireObject<CliContext>()

    init {
        subcommands(DevicesRemoveCommand(), DevicesBlockCommand(), DevicesUnblockCommand())
    }

    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        runCli { client ->
            val devices =
                client.getBody(
                    "/cli/devices",
                    ListSerializer(DeviceSummary.serializer()),
                )

            if (ctx.json) {
                echo(
                    cliJson.encodeToString(
                        ListSerializer(DeviceSummary.serializer()),
                        devices,
                    ),
                )
            } else {
                printDevices(devices)
            }
        }
    }

    private fun printDevices(devices: List<DeviceSummary>) {
        if (devices.isEmpty()) {
            echo("No paired devices.")
            return
        }
        echo("${devices.size} device(s):")
        echo("")
        for (device in devices) {
            val state = styleConnectState(connectStateName(device.connectState))
            val name = device.noteName ?: device.deviceName
            val addr = device.connectHostAddress?.let { "$it:${device.port}" } ?: "-"
            val send = if (device.allowSend) "send" else ""
            val recv = if (device.allowReceive) "recv" else ""
            val perms = listOf(send, recv).filter { it.isNotEmpty() }.joinToString(",")
            echo("  $name")
            echo("    Platform:  ${device.platform}")
            echo("    Version:   v${device.appVersion}")
            echo("    State:     $state")
            echo("    Address:   $addr")
            echo("    Perms:     $perms")
            echo("")
        }
    }
}

/** `devices remove <id>`: unpair a device; it stops syncing until paired again. */
class DevicesRemoveCommand : CliktCommand(name = "remove") {

    override fun help(context: Context): String = "Unpair a device (it stops syncing until paired again)"

    private val ctx by requireObject<CliContext>()

    private val device by argument(help = "App instance id of a paired device; a unique prefix is enough")

    override fun run() =
        runCli { client ->
            val paired = client.getBody("/cli/devices", ListSerializer(DeviceSummary.serializer()))
            val id = resolveOrFail(device, paired.map { it.appInstanceId }, "paired device")
            val response = client.deleteBody("/cli/devices/$id", MessageResponse.serializer())
            echoMessage(ctx, response)
        }
}

/**
 * `devices block <id>`: hide a nearby unpaired device from discovery, like Block on the
 * Devices page. Paired devices are removed, not blocked.
 */
class DevicesBlockCommand : CliktCommand(name = "block") {

    override fun help(context: Context): String = "Hide a nearby unpaired device from discovery"

    private val ctx by requireObject<CliContext>()

    private val device by argument(help = "App instance id of a nearby device; a unique prefix is enough")

    override fun run() =
        runCli { client ->
            val nearby = client.getBody("/cli/pair/nearby", ListSerializer(NearbyDeviceSummary.serializer()))
            val id = resolveOrFail(device, nearby.map { it.appInstanceId }, "nearby device")
            val response = client.postBody("/cli/devices/$id/block", "", MessageResponse.serializer())
            echoMessage(ctx, response)
        }
}

/** `devices unblock <id>`: let a blocked device show up in discovery again. */
class DevicesUnblockCommand : CliktCommand(name = "unblock") {

    override fun help(context: Context): String = "Let a blocked device show up in discovery again"

    private val ctx by requireObject<CliContext>()

    // The blocked list is not exposed, so the full id is required here.
    private val device by argument(help = "App instance id of the blocked device")

    override fun run() =
        runCli { client ->
            val response = client.deleteBody("/cli/devices/$device/block", MessageResponse.serializer())
            echoMessage(ctx, response)
        }
}

internal sealed class DeviceIdResolution {
    data class Resolved(
        val id: String,
    ) : DeviceIdResolution()

    data object NotFound : DeviceIdResolution()

    data class Ambiguous(
        val ids: List<String>,
    ) : DeviceIdResolution()
}

/**
 * Match [input] against known app instance ids: an exact match wins, otherwise a
 * case-insensitive prefix must identify exactly one id. Ids are long UUID-like strings,
 * so a short prefix is the practical way to name one from a terminal.
 */
internal fun resolveDeviceId(
    input: String,
    ids: List<String>,
): DeviceIdResolution {
    ids.firstOrNull { it == input }?.let { return DeviceIdResolution.Resolved(it) }
    val matches = ids.filter { it.startsWith(input, ignoreCase = true) }
    return when (matches.size) {
        0 -> DeviceIdResolution.NotFound
        1 -> DeviceIdResolution.Resolved(matches.single())
        else -> DeviceIdResolution.Ambiguous(matches)
    }
}

private fun CliktCommand.resolveOrFail(
    input: String,
    ids: List<String>,
    kind: String,
): String =
    when (val resolution = resolveDeviceId(input, ids)) {
        is DeviceIdResolution.Resolved -> resolution.id
        DeviceIdResolution.NotFound -> throw usageError("no $kind matches '$input'")
        is DeviceIdResolution.Ambiguous ->
            throw usageError("'$input' matches several devices: ${resolution.ids.joinToString(", ")}")
    }

private fun styleConnectState(name: String): String =
    when (name) {
        "Connected" -> TextColors.green(name)
        "Connecting" -> TextColors.yellow(name)
        else -> TextColors.red(name)
    }

private fun connectStateName(state: Int): String =
    when (state) {
        0 -> "Connected"
        1 -> "Connecting"
        2 -> "Disconnected"
        3 -> "Unmatched"
        4 -> "Unverified"
        5 -> "Incompatible"
        else -> "Unknown"
    }
