// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.openqa.selenium.manager

import org.openqa.selenium.Beta
import org.openqa.selenium.BuildInfo
import org.openqa.selenium.Platform
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.json.Json
import org.openqa.selenium.json.JsonException
import org.openqa.selenium.os.ExternalProcess
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.Volatile

/**
 * This implementation is still in beta, and may change.
 *
 *
 * The Selenium-Manager binaries are distributed in a JAR file
 * (org.openqa.selenium:selenium-manager) for the Java binding language. Since these binaries are
 * compressed within these JAR, we need to serialize the proper binary for the current platform
 * (Windows, macOS, or Linux) as an executable file. To implement this we use a singleton pattern,
 * since this way, we have a single instance in the JVM, and we reuse the resulting binary for all
 * the calls to the Selenium Manager singleton during all the Java process lifetime, deleting the
 * binary (stored as a local temporal file) on runtime shutdown.
 */
@Beta
class SeleniumManager private constructor() {
    private val managerPath: String? = System.getenv("SE_MANAGER_PATH")
    private var binary = if (managerPath == null) null else Paths.get(managerPath)
    private val seleniumManagerVersion: String
    private var binaryInTemporalFolder = false

    /** Wrapper for the Selenium Manager binary.  */
    init {
        val info = BuildInfo()
        val releaseLabel = info.releaseLabel
        val lastDot = releaseLabel.lastIndexOf(".")
        seleniumManagerVersion = BETA_PREFIX + releaseLabel.substring(0, lastDot)
        if (managerPath == null) {
            Runtime.getRuntime()
                .addShutdownHook(
                    Thread {
                        if (binaryInTemporalFolder && binary != null && Files.exists(binary)) {
                            try {
                                Files.delete(binary)
                            } catch (e: IOException) {
                                LOG.warning(
                                    String.format(
                                        "%s deleting temporal file: %s",
                                        e.javaClass.simpleName,
                                        e.message,
                                    ),
                                )
                            }
                        }
                    },
                )
        } else {
            LOG.fine(String.format("Selenium Manager set by env 'SE_MANAGER_PATH': %s", managerPath))
        }
    }

    /**
     * Determines the correct Selenium Manager binary to use.
     *
     * @return the path to the Selenium Manager binary.
     */
    @Synchronized
    private fun getBinary(): Path? {
        if (binary == null) {
            try {
                val current = Platform.getCurrent()
                var folder = ""
                var extension = ""
                if (current.`is`(Platform.WINDOWS)) {
                    extension = EXE
                    folder = "windows"
                } else if (current.`is`(Platform.MAC)) {
                    folder = "macos"
                } else if (current.`is`(Platform.LINUX)) {
                    folder = "linux"
                } else if (current.`is`(Platform.UNIX)) {
                    LOG.warning(
                        String.format(
                            "Selenium Manager binary may not be compatible with %s; verify settings",
                            current,
                        ),
                    )
                    folder = "linux"
                } else {
                    throw WebDriverException("Unsupported platform: $current")
                }

                binary = getBinaryInCache(SELENIUM_MANAGER + extension)
                if (!binary!!.toFile().exists()) {
                    val binaryPathInJar = String.format("%s/%s%s", folder, SELENIUM_MANAGER, extension)
                    this.javaClass.getResourceAsStream(binaryPathInJar).use { inputStream ->
                        binary!!.parent.toFile().mkdirs()
                        Files.copy(inputStream, binary)
                    }
                }
            } catch (e: Exception) {
                throw WebDriverException("Unable to obtain Selenium Manager Binary", e)
            }
        } else if (!Files.exists(binary)) {
            throw WebDriverException(
                String.format("Unable to obtain Selenium Manager Binary at: %s", binary),
            )
        }
        binary!!.toFile().setExecutable(true)

        LOG.fine(String.format("Selenium Manager binary found at: %s", binary))
        return binary
    }

    /**
     * Executes Selenium Manager to get the locations of the requested assets
     *
     * @param arguments List of command line arguments to send to Selenium Manager binary
     * @return the locations of the assets from Selenium Manager execution
     */
    fun getBinaryPaths(arguments: List<String>): SeleniumManagerOutput.Result {
        val args: MutableList<String> = ArrayList(arguments.size + 5)
        args.addAll(arguments)
        args.add("--language-binding")
        args.add("java")
        args.add("--output")
        args.add("json")

        if (logLevel.intValue() <= Level.FINE.intValue()) {
            args.add("--debug")
        }

        return runCommand(getBinary(), args)
    }

    private val logLevel: Level
        get() {
            var level = LOG.level
            if (level == null && LOG.parent != null) {
                level = LOG.parent.level
            }
            if (level == null) {
                return Level.INFO
            }
            return level
        }

    @Throws(IOException::class)
    private fun getBinaryInCache(binaryName: String): Path {
        // Look for cache path as system property or env

        var cachePath = System.getProperty(CACHE_PATH_ENV, "")
        if (cachePath!!.isEmpty()) cachePath = System.getenv(CACHE_PATH_ENV)
        if (cachePath == null) cachePath = DEFAULT_CACHE_PATH

        cachePath = cachePath.replace(HOME, System.getProperty("user.home"))

        // If cache path is not writable, SM will be extracted to a temporal folder
        var cacheParent = Paths.get(cachePath)
        if (!Files.isWritable(cacheParent)) {
            cacheParent = Files.createTempDirectory(SELENIUM_MANAGER)
            binaryInTemporalFolder = true
        }

        return Paths.get(
            cacheParent.toString(),
            String.format(BINARY_PATH_FORMAT, seleniumManagerVersion, binaryName),
        )
    }

    companion object {
        private val LOG: Logger = Logger.getLogger(SeleniumManager::class.java.name)

        private const val SELENIUM_MANAGER = "selenium-manager"
        private const val DEFAULT_CACHE_PATH = "~/.cache/selenium"
        private const val BINARY_PATH_FORMAT = "/manager/%s/%s"
        private const val HOME = "~"
        private const val CACHE_PATH_ENV = "SE_CACHE_PATH"
        private const val BETA_PREFIX = "0."
        private const val EXE = ".exe"
        private const val SE_ENV_PREFIX = "SE_"

        @Volatile
        private var manager: SeleniumManager? = null

        @JvmStatic
        fun getInstance(): SeleniumManager {
            if (manager == null) {
                synchronized(SeleniumManager::class.java) {
                    if (manager == null) {
                        manager =
                            SeleniumManager()
                    }
                }
            }
            return manager!!
        }

        /**
         * Executes a process with the given arguments.
         *
         * @param arguments the file and arguments to execute.
         * @return the standard output of the execution.
         */
        private fun runCommand(
            binary: Path?,
            arguments: List<String>,
        ): SeleniumManagerOutput.Result {
            LOG.fine(String.format("Executing Process: %s", arguments))

            val output: String
            val code: Int
            try {
                val processBuilder = ExternalProcess.builder()

                val properties = System.getProperties()
                for (name in properties.stringPropertyNames()) {
                    if (name.startsWith(SE_ENV_PREFIX)) {
                        // read property with 'default' value due to concurrency
                        val value = properties.getProperty(name, "")
                        if (!value.isEmpty()) {
                            processBuilder.environment(name, value)
                        }
                    }
                }
                val process =
                    processBuilder.command(binary!!.toAbsolutePath().toString(), arguments).start()

                if (!process.waitFor(Duration.ofHours(1))) {
                    LOG.warning("Selenium Manager did not exit, shutting it down")
                    process.shutdown()
                }
                code = process.exitValue()
                output = process.getOutput(StandardCharsets.UTF_8)
            } catch (e: Exception) {
                throw WebDriverException("Failed to run command: $arguments", e)
            }
            var jsonOutput: SeleniumManagerOutput? = null
            var failedToParse: JsonException? = null
            var dump = output
            if (!output.isEmpty()) {
                try {
                    jsonOutput =
                        Json().toType(
                            output,
                            SeleniumManagerOutput::class.java,
                        )
                    jsonOutput?.logs?.forEach(
                        Consumer { logged: SeleniumManagerOutput.Log ->
                            val currentLevel =
                                if (logged.level === Level.INFO) Level.FINE else logged.level
                            LOG.log(
                                currentLevel,
                                logged.message,
                            )
                        },
                    )
                    dump = jsonOutput?.result?.message ?: output
                } catch (e: JsonException) {
                    failedToParse = e
                }
            }
            if (code != 0) {
                throw WebDriverException(
                    "Command failed with code: $code, executed: $arguments\n$dump",
                    failedToParse,
                )
            } else if (failedToParse != null || jsonOutput == null) {
                throw WebDriverException(
                    "Failed to parse json output, executed: $arguments\n$dump",
                    failedToParse,
                )
            }
            return jsonOutput.result!!
        }
    }
}
