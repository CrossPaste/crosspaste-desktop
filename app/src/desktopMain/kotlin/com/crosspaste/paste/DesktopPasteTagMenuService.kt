package com.crosspaste.paste

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.paste.PasteTagScope
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.dzirbel.contextmenu.ContextMenuDivider
import com.dzirbel.contextmenu.ContextMenuParams
import com.dzirbel.contextmenu.GenericContextMenuItem
import kotlinx.coroutines.launch

class DesktopPasteTagMenuService(
    private val copywriter: GlobalCopywriter,
    private val pasteDao: PasteDao,
) {

    fun menuItemsProvider(tagScope: PasteTagScope): () -> List<ContextMenuItem> =
        {
            listOf(
                ContextMenuItem(copywriter.getText("rename")) {
                    tagScope.startEditing()
                },
                TagColorsMenuItem(tagScope, pasteDao),
                ContextMenuDivider,
                ContextMenuItem(copywriter.getText("delete")) {
                    pasteDao.deletePasteTagBlock(tagScope.tag.id)
                },
            )
        }
}

class TagColorsMenuItem(
    private val tagScope: PasteTagScope,
    private val pasteDao: PasteDao,
) : GenericContextMenuItem() {
    @Composable
    override fun Content(
        onDismissRequest: () -> Unit,
        params: ContextMenuParams,
        modifier: Modifier,
    ) {
        val colors: List<Long> = PasteTag.colors
        val tagColor = tagScope.tag.color
        val scope = rememberCoroutineScope()
        Row(
            modifier = modifier.padding(params.measurements.itemPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.forEach { colorVal ->
                val color = Color(colorVal.toInt())
                val isSelected = (tagColor == colorVal)

                ColorDot(
                    color = color,
                    isSelected = isSelected,
                    onClick = {
                        scope.launch {
                            pasteDao.updatePasteTagColor(tagScope.tag.id, colorVal)
                        }
                        onDismissRequest()
                    },
                )
            }
        }
    }

    @Composable
    private fun ColorDot(
        color: Color,
        isSelected: Boolean,
        onClick: () -> Unit,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(xLarge)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
        ) {
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .border(
                                width = tiny,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape,
                            ),
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(large2X)
                        .background(color = color, shape = CircleShape),
            )
        }
    }
}
