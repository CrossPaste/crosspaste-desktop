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
import com.crosspaste.db.sync.SyncRuntimeInfo
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
fun DeviceScope.DeviceInfoSection() {
    val copywriter = koinInject<GlobalCopywriter>()

    val tableData =
        remember(syncRuntimeInfo) {
            syncRuntimeInfo.toTableData(copywriter)
        }

    Column(verticalArrangement = Arrangement.spacedBy(small2X)) {
        SectionHeader("base_info")

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

fun SyncRuntimeInfo.toTableData(copywriter: GlobalCopywriter): TableData =
    object : TableData {
        override fun getData(): List<TableRow> =
            listOf(
                TableRowImpl(
                    listOf(
                        copywriter.getText("app_version"),
                        appVersion,
                    ),
                ),
                TableRowImpl(
                    listOf(
                        copywriter.getText("user_name"),
                        userName,
                    ),
                ),
                TableRowImpl(
                    listOf(
                        copywriter.getText("device_id"),
                        deviceId,
                    ),
                ),
                TableRowImpl(
                    listOf(
                        copywriter.getText("arch"),
                        platform.arch,
                    ),
                ),
                TableRowImpl(
                    listOf(
                        copywriter.getText("connect_host"),
                        connectHostAddress ?: copywriter.getText("unknown"),
                    ),
                ),
                TableRowImpl(
                    listOf(
                        copywriter.getText("port"),
                        if (port <= 0) {
                            copywriter.getText("unknown")
                        } else {
                            port.toString()
                        },
                    ),
                ),
            )
    }
