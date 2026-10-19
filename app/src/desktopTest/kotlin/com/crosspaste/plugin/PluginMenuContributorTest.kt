package com.crosspaste.plugin

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginMenuContributorTest {

    private fun plugin(
        id: String,
        pasteTypes: List<String>,
        enabled: Boolean = true,
        missing: List<String> = emptyList(),
        actions: List<PluginAction> = emptyList(),
    ) = LocalPlugin(
        directory = "/plugins/$id".toPath(),
        manifest =
            PluginManifest(
                id = id,
                version = "1.0.0",
                name = mapOf("en" to "$id-en", "zh" to "$id-zh"),
                pasteTypes = pasteTypes,
                runtime = "process",
                output = "replaceClipboard",
                actions = actions,
            ),
        problem = null,
        missingRequirements = missing,
        enabled = enabled,
    )

    @Test
    fun `offers runnable plugins matching the paste type in the current language`() {
        val repository =
            mockk<PluginRepository> {
                every { plugins } returns
                    MutableStateFlow(
                        listOf(
                            plugin("text", listOf("text", "html")),
                            plugin("image", listOf("image")),
                            plugin("disabled", listOf("text"), enabled = false),
                            plugin("missing", listOf("text"), missing = listOf("jq")),
                        ),
                    )
            }
        val copywriter = mockk<GlobalCopywriter> { every { language() } returns "zh" }
        val contributor = PluginMenuContributor(copywriter, repository, mockk())

        val textPaste = mockk<PasteData> { every { getType() } returns PasteType.TEXT_TYPE }
        val urlPaste = mockk<PasteData> { every { getType() } returns PasteType.URL_TYPE }

        assertEquals(listOf("text-zh"), contributor.contribute(textPaste).map { it.label })
        assertEquals(emptyList(), contributor.contribute(urlPaste).map { it.label })
    }

    @Test
    fun `plugins with actions become a submenu that runs the chosen action`() =
        runTest {
            val translate =
                plugin(
                    "translate",
                    listOf("text"),
                    actions =
                        listOf(
                            PluginAction("en", mapOf("en" to "English")),
                            PluginAction("zh-CN", mapOf("zh" to "简体中文")),
                        ),
                )
            val simple = plugin("simple", listOf("text"))
            val repository =
                mockk<PluginRepository> { every { plugins } returns MutableStateFlow(listOf(translate, simple)) }
            val copywriter = mockk<GlobalCopywriter> { every { language() } returns "en" }
            val runner = mockk<ProcessPluginRunner>(relaxed = true)
            val contributor = PluginMenuContributor(copywriter, repository, runner)
            val textPaste = mockk<PasteData> { every { getType() } returns PasteType.TEXT_TYPE }

            val (group, single) = contributor.contribute(textPaste)
            assertEquals("translate-en", group.label)
            assertEquals(listOf("English", "简体中文"), group.children.map { it.label })
            assertEquals(emptyList(), single.children)

            group.children[1].onClick()
            single.onClick()

            coVerify { runner.run(translate, textPaste, "zh-CN") }
            coVerify { runner.run(simple, textPaste, null) }
        }
}
