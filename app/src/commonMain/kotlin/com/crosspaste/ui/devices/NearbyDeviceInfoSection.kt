package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.InfoItem
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.base.TableData
import com.crosspaste.ui.base.TableRow
import com.crosspaste.ui.base.TableRowImpl
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@Composable
fun SyncScope.NearbyDeviceInfoSection() {
    val copywriter = koinInject<GlobalCopywriter>()

    val tableData =
        remember(syncInfo) {
            syncInfo.toTableData(copywriter)
        }

    Column(verticalArrangement = Arrangement.spacedBy(small2X)) {
        SectionHeader(text = copywriter.getText("base_info"))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        ) {
            val data = tableData.getData()

            Column(modifier = Modifier.padding(horizontal = large2X, vertical = tiny)) {
                data.forEachIndexed { index, row ->
                    val columns = row.getColumns()
                    InfoItem(columns[0], columns[1])
                    if (index < data.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

fun SyncInfo.toTableData(copywriter: GlobalCopywriter): TableData =
    object : TableData {

        private val appVersion =
            TableRowImpl(
                listOf(
                    copywriter.getText("app_version"),
                    appInfo.appVersion,
                ),
            )

        private val userName =
            TableRowImpl(
                listOf(
                    copywriter.getText("user_name"),
                    appInfo.userName,
                ),
            )

        private val deviceId =
            TableRowImpl(
                listOf(
                    copywriter.getText("device_id"),
                    endpointInfo.deviceId,
                ),
            )

        private val arch =
            TableRowImpl(
                listOf(
                    copywriter.getText("arch"),
                    endpointInfo.platform.arch,
                ),
            )

        private val hostList =
            run {
                val hostSize = endpointInfo.hostInfoList.size
                when (hostSize) {
                    0 -> {
                        null
                    }
                    1 -> {
                        listOf(
                            TableRowImpl(
                                listOf(
                                    "IP",
                                    endpointInfo.hostInfoList[0].hostAddress,
                                ),
                            ),
                        )
                    }
                    else -> {
                        endpointInfo.hostInfoList
                            .mapIndexed { index, info ->
                                TableRowImpl(
                                    listOf(
                                        "IP $index",
                                        info.hostAddress,
                                    ),
                                )
                            }
                    }
                }
            }

        private val port =
            TableRowImpl(
                listOf(
                    copywriter.getText("port"),
                    endpointInfo.port.toString(),
                ),
            )

        override fun getData(): List<TableRow> =
            listOfNotNull(
                appVersion,
                userName,
                deviceId,
                arch,
            ) + (hostList ?: listOf()) + listOf(port)
    }
