package org.openqa.selenium.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeleniumManagerOutput(
    val logs: List<Log>,
    val result: Result,
) {

    @Serializable
    data class Log(
        val level: String,
        val timestamp: Long,
        val message: String,
    )

    @Serializable
    data class Result(
        val code: Int,
        val message: String?,
        @SerialName("driver_path") val driverPath: String?,
        @SerialName("browser_path") val browserPath: String?,
    ) {

        constructor(driverPath: String?) : this(0, null, driverPath, null)
    }
}
