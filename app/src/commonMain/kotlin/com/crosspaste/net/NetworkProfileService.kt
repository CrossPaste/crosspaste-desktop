package com.crosspaste.net

interface NetworkProfileService {

    suspend fun getCurrentProfile(): NetworkProfile

    fun openNetworkSettings()
}

enum class NetworkProfile {
    PUBLIC,
    PRIVATE,
    DOMAIN_AUTHENTICATED,
    UNKNOWN,
    NOT_APPLICABLE,
    ;

    fun isPublic(): Boolean = this == PUBLIC
}
