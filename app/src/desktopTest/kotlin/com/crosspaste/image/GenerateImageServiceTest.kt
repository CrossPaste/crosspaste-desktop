package com.crosspaste.image

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateImageServiceTest {

    @Test
    fun `awaitGeneration returns true when file already exists`() =
        runTest {
            val tempFile = Files.createTempFile("img", ".png")
            tempFile.toFile().deleteOnExit()
            val path = tempFile.toOkioPath()

            val service = GenerateImageService()
            val result = service.awaitGeneration(path, timeoutMillis = 1000)
            assertTrue(result)
        }

    @Test
    fun `awaitGeneration times out for non-existent file without completion`() =
        runTest {
            val tempDir = Files.createTempDirectory("imgService")
            tempDir.toFile().deleteOnExit()
            val path = tempDir.toOkioPath().resolve("nonexistent.png")

            val service = GenerateImageService()
            val result = service.awaitGeneration(path, timeoutMillis = 100)
            assertFalse(result)
        }

    @Test
    fun `awaitGeneration returns true when marked complete before timeout`() =
        runTest {
            val tempDir = Files.createTempDirectory("imgComplete")
            tempDir.toFile().deleteOnExit()
            val path = tempDir.toOkioPath().resolve("pending.png")

            val service = GenerateImageService()

            // Start awaiting in a separate coroutine
            val deferred =
                async {
                    service.awaitGeneration(path, timeoutMillis = 5000)
                }

            // Give some time for awaitGeneration to register
            delay(50)

            // Mark generation complete
            service.markGenerationComplete(path)

            val result = deferred.await()
            assertTrue(result)
        }

    @Test
    fun `markGenerationComplete for unknown path does not throw`() =
        runTest {
            val tempDir = Files.createTempDirectory("imgUnknown")
            tempDir.toFile().deleteOnExit()
            val path = tempDir.toOkioPath().resolve("unknown.png")

            val service = GenerateImageService()
            // Should not throw even if no one is waiting
            service.markGenerationComplete(path)
        }

    @Test
    fun `multiple awaiters on same path all receive completion`() =
        runTest {
            val tempDir = Files.createTempDirectory("imgMulti")
            tempDir.toFile().deleteOnExit()
            val path = tempDir.toOkioPath().resolve("multi.png")

            val service = GenerateImageService()

            val deferred1 = async { service.awaitGeneration(path, timeoutMillis = 5000) }
            val deferred2 = async { service.awaitGeneration(path, timeoutMillis = 5000) }

            delay(50)

            service.markGenerationComplete(path)

            assertTrue(deferred1.await())
            assertTrue(deferred2.await())
        }

    @Test
    fun `concurrent awaitGeneration and markGenerationComplete`() =
        runTest {
            val tempDir = Files.createTempDirectory("imgConcurrent")
            tempDir.toFile().deleteOnExit()
            val service = GenerateImageService()

            // Test multiple paths concurrently
            val results =
                (1..5).map { i ->
                    val path = tempDir.toOkioPath().resolve("concurrent_$i.png")
                    val deferred = async { service.awaitGeneration(path, timeoutMillis = 5000) }
                    launch {
                        delay(20)
                        service.markGenerationComplete(path)
                    }
                    deferred
                }

            results.forEach { assertTrue(it.await()) }
        }
}
