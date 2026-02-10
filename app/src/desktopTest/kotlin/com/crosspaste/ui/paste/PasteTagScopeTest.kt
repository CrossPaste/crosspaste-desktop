package com.crosspaste.ui.paste

import com.crosspaste.paste.PasteTag
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasteTagScopeTest {

    @BeforeTest
    fun setUp() {
        // Reset global editing state before each test
        PasteTagScope.resetEditing()
    }

    @Test
    fun `startEditing sets tag as editing`() {
        val tag = PasteTag(id = 1L, name = "work", color = 0xFF0000, sortOrder = 0)
        val scope = createPasteTagScope(tag)
        scope.startEditing()
        assertTrue(PasteTagScope.isEditingMap.value.containsKey(1L))
        assertTrue(PasteTagScope.isEditingMap.value[1L] == true)
    }

    @Test
    fun `stopEditing removes tag from editing map`() {
        val tag = PasteTag(id = 1L, name = "work", color = 0xFF0000, sortOrder = 0)
        val scope = createPasteTagScope(tag)
        scope.startEditing()
        scope.stopEditing()
        assertFalse(PasteTagScope.isEditingMap.value.containsKey(1L))
    }

    @Test
    fun `starting edit on one tag clears other tags`() {
        val tag1 = PasteTag(id = 1L, name = "work", color = 0xFF0000, sortOrder = 0)
        val tag2 = PasteTag(id = 2L, name = "personal", color = 0x00FF00, sortOrder = 1)
        val scope1 = createPasteTagScope(tag1)
        val scope2 = createPasteTagScope(tag2)

        scope1.startEditing()
        assertTrue(PasteTagScope.isEditingMap.value.containsKey(1L))

        // Starting edit on tag2 should replace tag1
        scope2.startEditing()
        assertFalse(PasteTagScope.isEditingMap.value.containsKey(1L))
        assertTrue(PasteTagScope.isEditingMap.value.containsKey(2L))
    }

    @Test
    fun `resetEditing clears all editing state`() {
        val tag1 = PasteTag(id = 1L, name = "a", color = 0, sortOrder = 0)
        val scope1 = createPasteTagScope(tag1)
        scope1.startEditing()

        PasteTagScope.resetEditing()
        assertTrue(PasteTagScope.isEditingMap.value.isEmpty())
    }

    @Test
    fun `only one tag can be editing at a time`() {
        val tags = (1L..5L).map { PasteTag(id = it, name = "tag$it", color = 0, sortOrder = it) }
        val scopes = tags.map { createPasteTagScope(it) }

        // Start editing each in sequence
        for (scope in scopes) {
            scope.startEditing()
        }

        // Only the last tag should be editing
        val editingMap = PasteTagScope.isEditingMap.value
        assertEquals(1, editingMap.size)
        assertTrue(editingMap.containsKey(5L))
    }

    @Test
    fun `stopEditing on non-editing tag is no-op`() {
        val tag = PasteTag(id = 1L, name = "work", color = 0xFF0000, sortOrder = 0)
        val scope = createPasteTagScope(tag)
        // Stop without starting
        scope.stopEditing()
        assertTrue(PasteTagScope.isEditingMap.value.isEmpty())
    }

    @Test
    fun `stopEditing preserves other tags in editing map`() {
        // Although startEditing replaces, test stopEditing's filterKeys behavior
        val tag1 = PasteTag(id = 1L, name = "a", color = 0, sortOrder = 0)
        val scope1 = createPasteTagScope(tag1)

        scope1.startEditing()
        // Manually add another entry to simulate edge case
        PasteTagScope.isEditingMap.value = mapOf(1L to true, 2L to true)

        scope1.stopEditing()
        // tag 2 should remain
        assertNull(PasteTagScope.isEditingMap.value[1L])
        assertTrue(PasteTagScope.isEditingMap.value.containsKey(2L))
    }
}
