package com.crosspaste.plugin

import com.crosspaste.paste.PasteType
import com.crosspaste.platform.Platform
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginCompatibilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val manifestJson =
        """
        {
          "id": "com.example.translate",
          "version": "1.2.0",
          "minAppVersion": "2.3.0",
          "name": { "en": "Translate", "zh": "翻译" },
          "pasteTypes": ["text", "html", "files", "future-type"],
          "runtime": "process",
          "headless": false,
          "hosts": ["api.example.com"],
          "platforms": {
            "macos":   { "arch": ["arm64", "x64"], "command": ["/bin/sh", "run.sh"], "requires": ["curl"] },
            "linux":   { "command": ["/bin/sh", "run.sh"], "requires": ["curl"] },
            "windows": { "arch": ["x64"], "command": ["powershell", "-NoProfile", "-File", "run.ps1"] }
          },
          "output": "replaceClipboard",
          "someFutureField": 1
        }
        """.trimIndent()

    private val manifest = json.decodeFromString(PluginManifest.serializer(), manifestJson)

    private fun env(
        name: String = Platform.MACOS,
        arch: String = "aarch64",
        appVersion: String = "2.3.0",
    ) = PluginEnvironment(
        platform = Platform(name, arch, 64, "15"),
        appVersion = appVersion,
        runtimes = setOf(PluginEnvironment.RUNTIME_PROCESS),
    )

    @Test
    fun `parses the documented manifest and ignores unknown fields and paste types`() {
        assertEquals("com.example.translate", manifest.id)
        assertEquals(listOf("/bin/sh", "run.sh"), manifest.platforms["macos"]?.command)
        assertEquals(listOf("api.example.com"), manifest.hosts)
        assertEquals(
            setOf(PasteType.TEXT_TYPE, PasteType.HTML_TYPE, PasteType.FILE_TYPE),
            manifest.supportedPasteTypes(),
        )
    }

    @Test
    fun `display name falls back from exact to base language to english`() {
        assertEquals("翻译", manifest.displayName("zh"))
        assertEquals("翻译", manifest.displayName("zh_hant"))
        assertEquals("翻译", manifest.displayName("zh-CN"))
        val bcpManifest = manifest.copy(name = mapOf("zh-Hans" to "简体", "en" to "English"))
        assertEquals("简体", bcpManifest.displayName("zh_Hans"))
        assertEquals("Translate", manifest.displayName("ja"))
        assertEquals("x", manifest.copy(id = "x", name = emptyMap()).displayName("en"))
    }

    @Test
    fun `actions parse in order and their names fall back like the plugin name`() {
        val withActions =
            json.decodeFromString(
                PluginManifest.serializer(),
                manifestJson.replace(
                    "\"output\":",
                    """"actions": [
                        { "id": "en", "name": { "en": "English" } },
                        { "id": "zh-CN", "name": { "zh": "简体中文" } }
                    ],
                    "output":""",
                ),
            )

        assertEquals(listOf("en", "zh-CN"), withActions.actions.map { it.id })
        assertEquals("简体中文", withActions.actions[1].displayName("zh_hant"))
        assertEquals("简体中文", withActions.actions[1].displayName("ja"))
        assertEquals("English", withActions.actions[0].displayName("zh"))
        assertNull(checkPluginCompatibility(withActions, env()))
        assertEquals(emptyList(), manifest.actions)
    }

    @Test
    fun `compatible on every declared platform with raw arch values`() {
        assertNull(checkPluginCompatibility(manifest, env()))
        assertNull(checkPluginCompatibility(manifest, env(arch = "x86_64")))
        assertNull(checkPluginCompatibility(manifest, env(name = Platform.LINUX, arch = "aarch64")))
        assertNull(checkPluginCompatibility(manifest, env(name = Platform.WINDOWS, arch = "amd64")))
    }

    @Test
    fun `undeclared platform and arch are rejected`() {
        assertEquals(
            PluginProblem.UnsupportedArch("arm64"),
            checkPluginCompatibility(manifest, env(name = Platform.WINDOWS, arch = "aarch64")),
        )
        assertEquals(
            PluginProblem.UnsupportedPlatform("Linux"),
            checkPluginCompatibility(manifest.copy(platforms = manifest.platforms - "linux"), env(Platform.LINUX)),
        )
        assertEquals(
            PluginProblem.UnsupportedPlatform("iPhone"),
            checkPluginCompatibility(manifest, env(name = Platform.IPHONE)),
        )
    }

    @Test
    fun `minAppVersion ignores the running version's pre-release suffix`() {
        assertEquals(PluginProblem.AppTooOld("2.3.0"), checkPluginCompatibility(manifest, env(appVersion = "2.2.2")))
        assertNull(checkPluginCompatibility(manifest, env(appVersion = "2.3.0-beta")))
        assertNull(checkPluginCompatibility(manifest, env(appVersion = "2.10.0")))
        assertNull(checkPluginCompatibility(manifest, env(appVersion = "dev")))
    }

    @Test
    fun `unsupported runtime and output are reported`() {
        assertEquals(
            PluginProblem.UnsupportedRuntime("js"),
            checkPluginCompatibility(manifest.copy(runtime = "js"), env()),
        )
        assertEquals(
            PluginProblem.UnsupportedOutput("speak"),
            checkPluginCompatibility(manifest.copy(output = "speak"), env()),
        )
    }

    @Test
    fun `invalid manifests are rejected before platform checks`() {
        val invalid =
            listOf(
                manifest.copy(id = "../escape"),
                manifest.copy(version = "latest"),
                manifest.copy(pasteTypes = listOf("video")),
                manifest.copy(platforms = mapOf("macos" to PluginPlatform(command = emptyList()))),
                manifest.copy(platforms = mapOf("macos" to PluginPlatform(command = listOf("")))),
                manifest.copy(platforms = mapOf("macos" to PluginPlatform(command = listOf("   ")))),
                manifest.copy(actions = listOf(PluginAction(" ", mapOf("en" to "Blank")))),
                manifest.copy(actions = listOf(PluginAction("invalid id", mapOf("en" to "Valid")))),
                manifest.copy(
                    actions = listOf(PluginAction("en", mapOf("en" to "A")), PluginAction("en", mapOf("en" to "B"))),
                ),
                manifest.copy(actions = listOf(PluginAction("en"))),
                manifest.copy(actions = listOf(PluginAction("en", mapOf("en" to "")))),
                manifest.copy(actions = listOf(PluginAction("en", mapOf("en" to "   ")))),
            )
        invalid.forEach {
            val problem = checkPluginCompatibility(it, env())
            assert(problem is PluginProblem.InvalidManifest) { "$it -> $problem" }
        }
    }

    @Test
    fun `an invalid action id is named in the problem`() {
        assertEquals(
            PluginProblem.InvalidManifest("invalid action id: bad id"),
            checkPluginCompatibility(
                manifest.copy(
                    actions =
                        listOf(
                            PluginAction("en", mapOf("en" to "A")),
                            PluginAction(
                                "bad id",
                                mapOf(
                                    "en" to "B",
                                ),
                            ),
                        ),
                ),
                env(),
            ),
        )
    }

    @Test
    fun `arch normalization`() {
        assertEquals("arm64", normalizePluginArch("aarch64"))
        assertEquals("x64", normalizePluginArch("amd64"))
        assertEquals("x64", normalizePluginArch("X86_64"))
        assertEquals("riscv64", normalizePluginArch("riscv64"))
    }
}
