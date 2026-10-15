package com.crosspaste.plugin

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.menu.PasteMenuAction
import com.crosspaste.paste.menu.PasteMenuContributor

/**
 * One menu entry per enabled, runnable plugin that declares the paste's type,
 * a submenu when the plugin declares actions. Reads only the cached plugin list.
 */
class PluginMenuContributor(
    private val copywriter: GlobalCopywriter,
    private val pluginRepository: PluginRepository,
    private val processPluginRunner: ProcessPluginRunner,
) : PasteMenuContributor {

    override fun contribute(pasteData: PasteData): List<PasteMenuAction> {
        val pasteType = pasteData.getType()
        return pluginRepository.plugins.value
            .filter { plugin -> plugin.runnable && pasteType in (plugin.manifest?.supportedPasteTypes() ?: emptySet()) }
            .mapNotNull { plugin ->
                val manifest = plugin.manifest ?: return@mapNotNull null
                val language = copywriter.language()
                val label = manifest.displayName(language)
                if (manifest.actions.isEmpty()) {
                    PasteMenuAction(label) { processPluginRunner.run(plugin, pasteData) }
                } else {
                    PasteMenuAction(
                        label = label,
                        children =
                            manifest.actions.map { action ->
                                PasteMenuAction(action.displayName(language)) {
                                    processPluginRunner.run(plugin, pasteData, action.id)
                                }
                            },
                    )
                }
            }
    }
}
