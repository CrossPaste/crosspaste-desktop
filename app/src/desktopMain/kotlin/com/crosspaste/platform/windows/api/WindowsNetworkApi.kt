package com.crosspaste.platform.windows.api

import com.crosspaste.net.NetworkProfile
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Dispatch
import com.sun.jna.platform.win32.COM.EnumVariant
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.Variant
import com.sun.jna.platform.win32.WTypes.BSTR
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.ptr.ShortByReference
import io.github.oshai.kotlinlogging.KotlinLogging

data class WindowsNetworkSnapshot(
    val profile: NetworkProfile,
    val mDnsAllowed: Boolean,
)

object WindowsNetworkApi {

    private val logger = KotlinLogging.logger {}

    // INetworkListManager — exposes the same NLM info that Get-NetConnectionProfile reads,
    // available since Windows Vista.
    private val CLSID_NETWORK_LIST_MANAGER = GUID("DCB00C01-570F-4A9B-8D69-199FDBA5723B")
    private val IID_INETWORK_LIST_MANAGER = IID("DCB00000-570F-4A9B-8D69-199FDBA5723B")

    // INetFwPolicy2 — Windows Firewall with Advanced Security, scriptable since Win7.
    private val CLSID_NET_FW_POLICY2 = GUID("E2B3C97F-6AE1-41AC-817A-F6F92166D7DD")
    private val IID_INET_FW_POLICY2 = IID("98325047-C671-4174-8D81-DEFCD3F03186")
    private val IID_INET_FW_RULE = IID("AF230D27-BABA-4E42-ACED-F524F22CFCE2")

    private const val CLSCTX_ALL = 0x17

    private const val NLM_ENUM_NETWORK_CONNECTED = 0x01
    private const val NLM_CAT_PUBLIC = 0
    private const val NLM_CAT_PRIVATE = 1
    private const val NLM_CAT_DOMAIN = 2

    private const val FW_PROFILE_DOMAIN = 0x1
    private const val FW_PROFILE_PRIVATE = 0x2
    private const val FW_PROFILE_PUBLIC = 0x4
    private const val FW_PROFILE_ALL = 0x7FFFFFFF

    private const val NET_FW_RULE_DIR_IN = 1
    private const val NET_FW_ACTION_ALLOW = 1

    // Locale-independent group resource id for the built-in "Network Discovery" rule set.
    // The DisplayGroup is localised ("Network Discovery" / "网络发现" / ...), but Grouping
    // exposes the raw resource id, which never changes.
    private const val NETWORK_DISCOVERY_GROUPING = "@FirewallAPI.dll,-32752"

    // S_OK and S_FALSE both grant a CoUninitialize obligation; RPC_E_CHANGED_MODE means
    // the thread is already initialised under a different threading model — we can still
    // make COM calls but must NOT call CoUninitialize.
    private const val RPC_E_CHANGED_MODE: Int = 0x80010106.toInt()

    fun query(): WindowsNetworkSnapshot {
        val hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
        val ownsCoInit = COMUtils.SUCCEEDED(hr)
        if (!ownsCoInit && hr.toInt() != RPC_E_CHANGED_MODE) {
            logger.warn { "CoInitializeEx failed: 0x${Integer.toHexString(hr.toInt())}" }
            return WindowsNetworkSnapshot(NetworkProfile.UNKNOWN, mDnsAllowed = false)
        }
        try {
            val profile =
                runCatching { queryProfile() }
                    .onFailure { logger.warn(it) { "queryProfile failed" } }
                    .getOrDefault(NetworkProfile.UNKNOWN)
            val mDnsAllowed =
                runCatching { queryMDnsAllowed(profile) }
                    .onFailure { logger.warn(it) { "queryMDnsAllowed failed" } }
                    .getOrDefault(false)
            return WindowsNetworkSnapshot(profile, mDnsAllowed)
        } finally {
            if (ownsCoInit) {
                Ole32.INSTANCE.CoUninitialize()
            }
        }
    }

    private fun queryProfile(): NetworkProfile {
        val ppNlm = PointerByReference()
        val hr =
            Ole32.INSTANCE.CoCreateInstance(
                CLSID_NETWORK_LIST_MANAGER,
                null,
                CLSCTX_ALL,
                IID_INETWORK_LIST_MANAGER,
                ppNlm,
            )
        if (!COMUtils.SUCCEEDED(hr)) {
            logger.warn { "CoCreateInstance(NetworkListManager) failed: 0x${Integer.toHexString(hr.toInt())}" }
            return NetworkProfile.UNKNOWN
        }
        val nlm = NetworkListManager(ppNlm.value)
        try {
            val ppEnum = PointerByReference()
            if (!COMUtils.SUCCEEDED(nlm.getNetworks(NLM_ENUM_NETWORK_CONNECTED, ppEnum))) {
                return NetworkProfile.UNKNOWN
            }
            val enumNetworks = EnumNetworks(ppEnum.value)
            try {
                return reduceToMostRestrictive(enumNetworks)
            } finally {
                enumNetworks.Release()
            }
        } finally {
            nlm.Release()
        }
    }

    private fun reduceToMostRestrictive(enumNetworks: EnumNetworks): NetworkProfile {
        var best = NetworkProfile.UNKNOWN
        while (true) {
            val ppNetwork = PointerByReference()
            val fetched = IntByReference()
            val hr = enumNetworks.next(1, ppNetwork, fetched)
            if (!COMUtils.SUCCEEDED(hr) || fetched.value != 1 || ppNetwork.value == null) break
            val network = Network(ppNetwork.value)
            try {
                val cat = IntByReference()
                if (COMUtils.SUCCEEDED(network.getCategory(cat))) {
                    val current =
                        when (cat.value) {
                            NLM_CAT_PUBLIC -> NetworkProfile.PUBLIC
                            NLM_CAT_PRIVATE -> NetworkProfile.PRIVATE
                            NLM_CAT_DOMAIN -> NetworkProfile.DOMAIN_AUTHENTICATED
                            else -> NetworkProfile.UNKNOWN
                        }
                    if (riskRank(current) > riskRank(best)) best = current
                }
            } finally {
                network.Release()
            }
        }
        return best
    }

    private fun riskRank(profile: NetworkProfile): Int =
        when (profile) {
            NetworkProfile.PUBLIC -> 3
            NetworkProfile.PRIVATE -> 2
            NetworkProfile.DOMAIN_AUTHENTICATED -> 1
            else -> 0
        }

    private fun queryMDnsAllowed(profile: NetworkProfile): Boolean {
        val profileBit = profile.firewallBit() ?: return false

        val ppPolicy = PointerByReference()
        val hr =
            Ole32.INSTANCE.CoCreateInstance(
                CLSID_NET_FW_POLICY2,
                null,
                CLSCTX_ALL,
                IID_INET_FW_POLICY2,
                ppPolicy,
            )
        if (!COMUtils.SUCCEEDED(hr)) {
            logger.warn { "CoCreateInstance(NetFwPolicy2) failed: 0x${Integer.toHexString(hr.toInt())}" }
            return false
        }

        val policy = NetFwPolicy2(ppPolicy.value)
        try {
            val ppRules = PointerByReference()
            if (!COMUtils.SUCCEEDED(policy.getRules(ppRules))) return false
            val rules = NetFwRules(ppRules.value)
            try {
                val ppEnum = PointerByReference()
                if (!COMUtils.SUCCEEDED(rules.getNewEnum(ppEnum))) return false

                // _NewEnum hands back IUnknown — promote it to IEnumVARIANT.
                val rawEnum = Unknown(ppEnum.value)
                val ppEnumVariant = PointerByReference()
                val qiHr = rawEnum.QueryInterface(REFIID(EnumVariant.IID), ppEnumVariant)
                rawEnum.Release()
                if (!COMUtils.SUCCEEDED(qiHr)) return false

                val enumVariant = EnumVariant(ppEnumVariant.value)
                try {
                    return scanForAllowingRule(enumVariant, profileBit)
                } finally {
                    enumVariant.Release()
                }
            } finally {
                rules.Release()
            }
        } finally {
            policy.Release()
        }
    }

    private fun scanForAllowingRule(
        enumVariant: EnumVariant,
        profileBit: Int,
    ): Boolean {
        while (true) {
            val batch = enumVariant.Next(BATCH_SIZE)
            if (batch.isEmpty()) break
            for (variant in batch) {
                try {
                    if (variant.getVarType().toInt() != Variant.VT_DISPATCH) continue
                    val dispatch = variant.getValue() as? Dispatch ?: continue
                    if (ruleAllowsDiscovery(dispatch, profileBit)) return true
                } finally {
                    // VariantClear releases the contained IDispatch/IUnknown reference.
                    OleAuto.INSTANCE.VariantClear(variant)
                }
            }
            if (batch.size < BATCH_SIZE) break
        }
        return false
    }

    private fun ruleAllowsDiscovery(
        dispatch: Dispatch,
        profileBit: Int,
    ): Boolean {
        val ppRule = PointerByReference()
        if (!COMUtils.SUCCEEDED(dispatch.QueryInterface(REFIID(IID_INET_FW_RULE), ppRule))) return false
        val rule = NetFwRule(ppRule.value)
        return try {
            val enabled = ShortByReference()
            if (!COMUtils.SUCCEEDED(rule.getEnabled(enabled))) return false
            if (enabled.value.toInt() == 0) return false // VARIANT_FALSE

            val direction = IntByReference()
            if (!COMUtils.SUCCEEDED(rule.getDirection(direction))) return false
            if (direction.value != NET_FW_RULE_DIR_IN) return false

            val action = IntByReference()
            if (!COMUtils.SUCCEEDED(rule.getAction(action))) return false
            if (action.value != NET_FW_ACTION_ALLOW) return false

            val profiles = IntByReference()
            if (!COMUtils.SUCCEEDED(rule.getProfiles(profiles))) return false
            val profilesValue = profiles.value
            val matchesProfile =
                profilesValue == FW_PROFILE_ALL || (profilesValue and profileBit) != 0
            if (!matchesProfile) return false

            // The "Network Discovery" toggle in Advanced sharing settings controls
            // exactly the rule group with this Grouping resource id. We deliberately
            // do NOT fall back to matching UDP/5353 — Windows 10 1809+ / Windows 11
            // ship a separate `mDNS-In-UDP` rule that is NOT part of this group, so
            // matching it would tell the user discovery is on when the toggle is off.
            val grouping = readBstr(rule::getGrouping) ?: return false
            grouping.equals(NETWORK_DISCOVERY_GROUPING, ignoreCase = true)
        } finally {
            rule.Release()
        }
    }

    private fun readBstr(getter: (PointerByReference) -> HRESULT): String? {
        val ref = PointerByReference()
        if (!COMUtils.SUCCEEDED(getter(ref))) return null
        val ptr = ref.value ?: return null
        val bstr = BSTR(ptr)
        return try {
            bstr.value
        } finally {
            OleAuto.INSTANCE.SysFreeString(bstr)
        }
    }

    private fun NetworkProfile.firewallBit(): Int? =
        when (this) {
            NetworkProfile.PUBLIC -> FW_PROFILE_PUBLIC
            NetworkProfile.PRIVATE -> FW_PROFILE_PRIVATE
            NetworkProfile.DOMAIN_AUTHENTICATED -> FW_PROFILE_DOMAIN
            else -> null
        }

    private const val BATCH_SIZE = 32
}

// ---- COM vtable wrappers ----
//
// Indices are counted from 0 and include the inherited IUnknown (3) and IDispatch (4)
// slots, so the first custom method on a dual interface lives at index 7.

private class NetworkListManager(
    pvInstance: Pointer,
) : Unknown(pvInstance) {
    fun getNetworks(
        flags: Int,
        out: PointerByReference,
    ): HRESULT =
        _invokeNativeObject(
            7,
            arrayOf<Any>(this.pointer, flags, out),
            HRESULT::class.java,
        ) as HRESULT
}

private class EnumNetworks(
    pvInstance: Pointer,
) : Unknown(pvInstance) {
    fun next(
        celt: Int,
        out: PointerByReference,
        fetched: IntByReference,
    ): HRESULT =
        _invokeNativeObject(
            8,
            arrayOf<Any>(this.pointer, celt, out, fetched),
            HRESULT::class.java,
        ) as HRESULT
}

private class Network(
    pvInstance: Pointer,
) : Unknown(pvInstance) {
    fun getCategory(out: IntByReference): HRESULT =
        _invokeNativeObject(
            18,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT
}

private class NetFwPolicy2(
    pvInstance: Pointer,
) : Unknown(pvInstance) {
    fun getRules(out: PointerByReference): HRESULT =
        _invokeNativeObject(
            18,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT
}

private class NetFwRules(
    pvInstance: Pointer,
) : Unknown(pvInstance) {
    fun getNewEnum(out: PointerByReference): HRESULT =
        _invokeNativeObject(
            11,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT
}

private class NetFwRule(
    pvInstance: Pointer,
) : Unknown(pvInstance) {
    fun getDirection(out: IntByReference): HRESULT =
        _invokeNativeObject(
            27,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT

    fun getEnabled(out: ShortByReference): HRESULT =
        _invokeNativeObject(
            33,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT

    fun getGrouping(out: PointerByReference): HRESULT =
        _invokeNativeObject(
            35,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT

    fun getProfiles(out: IntByReference): HRESULT =
        _invokeNativeObject(
            37,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT

    fun getAction(out: IntByReference): HRESULT =
        _invokeNativeObject(
            41,
            arrayOf<Any>(this.pointer, out),
            HRESULT::class.java,
        ) as HRESULT
}
