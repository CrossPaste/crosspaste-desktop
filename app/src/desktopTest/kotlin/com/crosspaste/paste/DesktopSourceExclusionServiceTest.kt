package com.crosspaste.paste

import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DesktopLocaleUtils
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSourceExclusionServiceTest {

    private fun newService(): DesktopSourceExclusionService {
        val configDir = Files.createTempDirectory("source-exclusion").toOkioPath()
        configDir.toFile().deleteOnExit()
        val manager = DesktopConfigManager(OneFilePersist(configDir.resolve("appConfig.json")), DesktopLocaleUtils)
        return DesktopSourceExclusionService(manager)
    }

    @Test
    fun `exact exclusions match the whole source only`() {
        val service = newService()
        service.addExclusion("Bitwarden")
        assertTrue(service.isExcluded("Bitwarden"))
        assertFalse(service.isExcluded("bitwarden"))
        assertFalse(service.isExcluded("Bitwarden Desktop"))
        assertFalse(service.isExcluded(null))
    }

    @Test
    fun `patterns match as case-insensitive substrings`() {
        val service = newService()
        assertTrue(service.addPattern("bitw"))
        assertTrue(service.isExcluded("Bitwarden"))
        assertTrue(service.isExcluded("org.BITWARDEN.desktop"))
        assertFalse(service.isExcluded("KeePassXC"))
    }

    @Test
    fun `patterns are trimmed and deduplicated ignoring case`() {
        val service = newService()
        assertTrue(service.addPattern("  Bitwarden "))
        assertFalse(service.addPattern("bitwarden"))
        assertFalse(service.addPattern("   "))
        assertEquals(listOf("Bitwarden"), service.getPatterns())
    }

    @Test
    fun `removing a pattern or exclusion resumes recording`() {
        val service = newService()
        service.addExclusion("KeePassXC")
        service.addPattern("bitw")
        service.removeExclusion("KeePassXC")
        service.removePattern("bitw")
        assertFalse(service.isExcluded("KeePassXC"))
        assertFalse(service.isExcluded("Bitwarden"))
        assertEquals(emptyList(), service.getExclusions())
        assertEquals(emptyList(), service.getPatterns())
    }

    @Test
    fun `exclusions are not added twice`() {
        val service = newService()
        service.addExclusion("Bitwarden")
        service.addExclusion("Bitwarden")
        assertEquals(listOf("Bitwarden"), service.getExclusions())
    }
}
