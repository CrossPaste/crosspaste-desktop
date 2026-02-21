package com.crosspaste.dto.cli

import kotlinx.serialization.Serializable

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

@Serializable
data class ConfigEntryDto(
    val key: String,
    val value: String,
)

@Serializable
data class ConfigUpdateRequest(
    val key: String,
    val value: String,
)

@Serializable
data class TagSummary(
    val id: Long,
    val name: String,
    val color: Long,
)

@Serializable
data class CreateTagRequest(
    val name: String,
    val color: Long? = null,
)
