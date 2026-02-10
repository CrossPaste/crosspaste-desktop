package com.crosspaste.cli.commands

import com.crosspaste.cli.CliContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import io.ktor.client.call.*
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

    override fun help(context: Context): String = "List paired devices"

    private val ctx by requireObject<CliContext>()

    override fun run() =
        runWithClient { client ->
            val response = client.get("/cli/devices")
            handleResponse(response) { resp ->
                val devices = resp.body<List<DeviceSummary>>()
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
            val state = connectStateName(device.connectState)
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
