package com.crosspaste.utils

object NetUtils {
    /**
     * Filter and format IP input while typing
     */
    fun formatIpInput(input: String): String {
        val filtered = input.filter { it.isDigit() || it == '.' }
        val parts = filtered.split(".")

        // Limit to 4 segments and max length of 15
        if (parts.size > 4 || filtered.length > 15) return "" // Or return previous state

        val validatedParts =
            parts.map { part ->
                if (part.isEmpty()) return@map ""
                val intVal = part.toIntOrNull() ?: return@map ""
                if (intVal > 255) return@map "255"
                // Keep leading zeros only if the user is currently typing them (like "192.168.0")
                // But prevent multiple zeros like "00"
                if (part.length > 1 && part.startsWith("0") && !part.startsWith("0.")) {
                    intVal.toString()
                } else {
                    part
                }
            }
        return validatedParts.joinToString(".")
    }

    /**
     * Strict check if the IP is a complete and valid IPv4 address
     */
    fun isValidIp(ip: String): Boolean {
        val ipv4Regex = """^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$""".toRegex()
        return ip.matches(ipv4Regex)
    }

    /**
     * Check if the port is within valid range (1-65535)
     */
    fun isValidPort(port: String): Boolean {
        val p = port.toIntOrNull() ?: return false
        return p in 1..65535
    }
}
