package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.base.TableData
import com.crosspaste.ui.base.TableRow
import com.crosspaste.ui.base.TableRowImpl
import com.crosspaste.ui.settings.SettingItemsTitleView
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.SettingsTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun SyncScope.NearbyDeviceDetailContentView() {
    val copywriter = koinInject<GlobalCopywriter>()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.appBackground)
                .clip(tinyRoundedCornerShape),
    ) {
        NearbyDeviceDetailHeaderView()

        Spacer(Modifier.height(medium))

        val tableData =
            remember(syncInfo) {
                syncInfo.toTableData(copywriter)
            }

        val settingsTextStyle = SettingsTextStyle()

        HighlightedCard(
            modifier =
                Modifier.wrapContentSize(),
            shape = tinyRoundedCornerShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = AppUIColors.generalBackground,
                ),
        ) {
            SettingItemsTitleView("base_info")

            val width = tableData.measureColumnWidth(0, settingsTextStyle)

            Column(
                modifier = Modifier.wrapContentSize(),
            ) {
                val data = tableData.getData()
                data.forEachIndexed { index, row ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(small2X),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val columns = row.getColumns()
                        Text(
                            modifier = Modifier.width(width),
                            text = columns[0],
                            style = settingsTextStyle,
                            color =
                                MaterialTheme.colorScheme.contentColorFor(
                                    AppUIColors.generalBackground,
                                ),
                        )
                        Spacer(modifier = Modifier.width(medium))
                        Text(
                            text = columns[1],
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                MaterialTheme.colorScheme.contentColorFor(
                                    AppUIColors.generalBackground,
                                ),
                        )
                    }
                    if (index < data.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = small))
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
