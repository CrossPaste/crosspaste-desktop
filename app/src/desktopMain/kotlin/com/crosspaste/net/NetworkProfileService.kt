package com.crosspaste.net

import kotlinx.coroutines.flow.StateFlow

interface NetworkProfileService {

    val diagnosis: StateFlow<NetworkDiagnosis>

    val isWarningDismissed: StateFlow<Boolean>

    val isWarningDialogVisible: StateFlow<Boolean>

    suspend fun refresh()

    fun dismissWarning()

    fun showWarning()

    fun openNetworkSettings()
}

data class NetworkDiagnosis(
    val profile: NetworkProfile,
    // null = could not be determined. Only an explicit `false` (rules enumerated,
    // no allowing rule found) is treated as blocking — null short-circuits to
    // avoid false-positive warnings on accounts that can't read firewall rules.
    val mDnsAllowed: Boolean?,
) {

    fun isLikelyBlocking(): Boolean =
        when (profile) {
            NetworkProfile.PUBLIC,
            NetworkProfile.PRIVATE,
            NetworkProfile.DOMAIN_AUTHENTICATED,
            -> mDnsAllowed == false
            NetworkProfile.UNKNOWN,
            NetworkProfile.NOT_APPLICABLE,
            -> false
        }

    fun fingerprint(): String = "${profile.name}|$mDnsAllowed"

    companion object {
        val NOT_APPLICABLE = NetworkDiagnosis(NetworkProfile.NOT_APPLICABLE, mDnsAllowed = null)
    }
}

enum class NetworkProfile {
    PUBLIC,
    PRIVATE,
    DOMAIN_AUTHENTICATED,
    UNKNOWN,
    NOT_APPLICABLE,
}
