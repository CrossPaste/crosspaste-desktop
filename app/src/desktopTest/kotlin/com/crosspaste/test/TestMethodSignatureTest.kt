package com.crosspaste.test

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JUnit only discovers `void` test methods and silently ignores the rest without
 * reporting them as skipped. A Kotlin test written as `= runBlocking { ... }` whose
 * last expression returns a value (e.g. `assertNotNull(x)`) compiles to a non-void
 * method and never runs.
 */
class TestMethodSignatureTest {

    private val testAnnotations = setOf("org.junit.jupiter.api.Test", "org.junit.Test")

    @Test
    fun `every test method returns void`() {
        val root =
            File(
                javaClass.protectionDomain.codeSource.location
                    .toURI(),
            )
        val loader = javaClass.classLoader

        val offenders =
            root
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map {
                    it
                        .relativeTo(root)
                        .path
                        .removeSuffix(".class")
                        .replace(File.separatorChar, '.')
                }.mapNotNull { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
                .flatMap { clazz ->
                    runCatching { clazz.declaredMethods.asSequence() }
                        .getOrDefault(emptySequence())
                        .filter { method -> method.annotations.any { it.annotationClass.java.name in testAnnotations } }
                        .filter { method -> method.returnType != Void.TYPE }
                        .map { method -> "${clazz.name}#${method.name} returns ${method.returnType.name}" }
                }.sorted()
                .toList()

        assertTrue(
            offenders.isEmpty(),
            "Test methods that JUnit will silently skip (use runTest or a block body):\n" +
                offenders.joinToString("\n"),
        )
    }
}
