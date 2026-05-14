package com.crosspaste.net

interface NetworkProfileService {

    suspend fun diagnose(): NetworkDiagnosis

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
