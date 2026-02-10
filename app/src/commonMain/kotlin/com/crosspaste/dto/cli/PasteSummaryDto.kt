package com.crosspaste.dto.cli

import kotlinx.serialization.Serializable

@Serializable
data class PasteSummaryDto(
    val id: Long,
    val typeName: String,
    val source: String?,
    val size: Long,
    val favorite: Boolean,
    val createTime: Long,
    val preview: String,
    val remote: Boolean,
)

@Serializable
data class PasteListResponse(
    val items: List<PasteSummaryDto>,
    val total: Long,
)

@Serializable
data class PasteDetailResponse(
    val id: Long,
    val typeName: String,
    val source: String?,
    val size: Long,
    val favorite: Boolean,
    val createTime: Long,
    val remote: Boolean,
    val hash: String,
    val content: String?,
)

@Serializable
data class CopyRequest(
    val text: String,
)
