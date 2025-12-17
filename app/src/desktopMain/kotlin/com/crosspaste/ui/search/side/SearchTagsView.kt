package com.crosspaste.ui.search.side

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.DesktopPasteTagMenuService
import com.crosspaste.paste.PasteTag
import com.crosspaste.paste.PasteTag.Companion.createDefaultPasteTag
import com.crosspaste.ui.LocalSearchWindowInfoState
import com.crosspaste.ui.base.PasteContextMenuView
import com.crosspaste.ui.base.add
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.paste.PasteTagScope
import com.crosspaste.ui.paste.createPasteTagScope
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchTagsView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDao = koinInject<PasteDao>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()
    val tagMenuService = koinInject<DesktopPasteTagMenuService>()

    val searchWindowInfo = LocalSearchWindowInfoState.current

    val searchBaseParams by pasteSearchViewModel.searchBaseParams.collectAsState()

    val tagList by pasteSearchViewModel.tagList.collectAsState()

    var newTag by remember { mutableStateOf<PasteTag?>(null) }

    val editingMap by PasteTagScope.isEditingMap

    val scope = rememberCoroutineScope()

    LaunchedEffect(searchWindowInfo.show) {
        newTag = null
        PasteTagScope.resetEditing()
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(tagList, key = { _, item -> item.id }) { _, tag ->
            val currentTag by rememberUpdatedState(tag)

            val isSelected = currentTag.id == searchBaseParams.tag

            val tagScope =
                remember(
                    currentTag.id,
                    currentTag.name,
                    currentTag.color,
                ) {
                    createPasteTagScope(currentTag)
                }

            if (editingMap[tag.id] == true) {
                tagScope.EditableTagChip { name ->
                    if (name.isBlank()) {
                        pasteDao.deletePasteTagBlock(tag.id)
                    } else {
                        pasteDao.updatePasteTagName(tag.id, name)
                    }
                    tagScope.stopEditing()
                }
            } else {
                PasteContextMenuView(
                    items = tagMenuService.menuItemsProvider(tagScope),
                ) {
                    tagScope.TagChip(
                        isSelected = isSelected,
                        onEdit = {
                            pasteSearchViewModel.updateTag(tag.id)
                        },
                    ) {
                        tagScope.startEditing()
                    }
                }
            }
        }

        newTag?.let {
            item {
                val scope = remember { createPasteTagScope(it) }

                scope.EditableTagChip { name ->
                    newTag = null
                    if (!name.isBlank()) {
                        pasteDao.createPasteTag(name, it.color)
                    }
                    PasteTagScope.resetEditing()
                }
            }
        }

        item {
            AssistChip(
                onClick = {
                    scope.launch {
                        newTag =
                            createDefaultPasteTag(
                                name = copywriter.getText("unnamed"),
                                maxSortOrder = pasteDao.getMaxSortOrder(),
                            )
                        PasteTagScope.resetEditing()
                    }
                },
                label = {
                    Icon(
                        painter = add(),
                        contentDescription = "Add",
                        modifier = Modifier.size(medium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier.height(xxLarge),
                border = null,
                shape = CircleShape,
            )
        }
    }
}
