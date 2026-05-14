package com.crosspaste.net

import kotlinx.coroutines.flow.StateFlow

interface NetworkProfileService {

    val diagnosis: StateFlow<NetworkDiagnosis>

    val isWarningDismissed: StateFlow<Boolean>

    suspend fun refresh()

    fun dismissWarning()

    fun showWarning()

    fun openNetworkSettings()
}

data class NetworkDiagnosis(
    val profile: NetworkProfile,
    val mDnsAllowed: Boolean,
) {

    fun isLikelyBlocking(): Boolean =
        when (profile) {
            NetworkProfile.PUBLIC -> true
            NetworkProfile.PRIVATE,
            NetworkProfile.DOMAIN_AUTHENTICATED,
            -> !mDnsAllowed
            NetworkProfile.UNKNOWN,
            NetworkProfile.NOT_APPLICABLE,
            -> false
        }

    fun fingerprint(): String = "${profile.name}|$mDnsAllowed"

    companion object {
        val NOT_APPLICABLE = NetworkDiagnosis(NetworkProfile.NOT_APPLICABLE, mDnsAllowed = true)
    }
}

enum class NetworkProfile {
    PUBLIC,
    PRIVATE,
    DOMAIN_AUTHENTICATED,
    UNKNOWN,
    NOT_APPLICABLE,
}
