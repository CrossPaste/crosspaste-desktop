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

import com.crosspaste.net.DesktopProxy
import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.openqa.selenium.Beta
import org.openqa.selenium.BuildInfo
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.json.JsonException
import org.openqa.selenium.os.ExternalProcess
import java.io.IOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
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

    private val platform = currentPlatform()
    private val fileName =
        if (platform.isWindows()) {
            "selenium-manager.exe"
        } else {
            "selenium-manager"
        }
    private var binary = DesktopAppPathProvider.pasteAppJarPath.resolve(fileName).toNioPath()
    private val seleniumManagerVersion: String
    private var binaryInTemporalFolder = false

    /** Wrapper for the Selenium Manager binary.  */
    init {
        val info = BuildInfo()
        val releaseLabel = info.releaseLabel
        val lastDot = releaseLabel.lastIndexOf(".")
        seleniumManagerVersion = BETA_PREFIX + releaseLabel.substring(0, lastDot)
    }

    /**
     * Determines the correct Selenium Manager binary to use.
     *
     * @return the path to the Selenium Manager binary.
     */
    @Synchronized
    private fun getBinary(): Path {
        return binary
    }

    /**
     * Executes Selenium Manager to get the locations of the requested assets
     *
     * @param arguments List of command line arguments to send to Selenium Manager binary
     * @return the locations of the assets from Selenium Manager execution
     */
    fun getBinaryPaths(arguments: List<String>): SeleniumManagerOutput.Result {
        val args: MutableList<String> = mutableListOf()
        args.addAll(arguments)
        args.add("--language-binding")
        args.add("java")
        args.add("--output")
        args.add("json")

        getBinaryByDefault(args).let {
            if (it.code != 0) {
                return getBinaryWithMirror(args)
            }
            return it
        }
    }

    private fun getBinaryByDefault(arguments: List<String>): SeleniumManagerOutput.Result {
        val args: MutableList<String> = mutableListOf()
        args.addAll(arguments)
        val uri = URL("https://storage.googleapis.com").toURI()
        val proxy = DesktopProxy.getProxy(uri)
        DesktopProxy.proxyToCommandLine(proxy)?.let {
            args.add("--proxy")
            args.add(it)
        }
        return runCommand(getBinary(), args)
    }

    private fun getBinaryWithMirror(arguments: List<String>): SeleniumManagerOutput.Result {
        val args: MutableList<String> = mutableListOf()
        args.addAll(arguments)
        args.add("--driver-mirror-url")
        args.add("https://oss.crosspaste.com")
        return runCommand(getBinary(), args)
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

        private val logger: KLogger = KotlinLogging.logger {}

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
            logger.info { String.format("Executing Process: %s", arguments) }

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
                    logger.warn { "Selenium Manager did not exit, shutting it down" }
                    process.shutdown()
                }
                code = process.exitValue()
                output = process.getOutput(StandardCharsets.UTF_8)
                logger.info { "code=$code\noutput=$output" }
            } catch (e: Exception) {
                throw WebDriverException("Failed to run command: $arguments", e)
            }

            try {
                val jsonOutput = getJsonUtils().JSON.decodeFromString<SeleniumManagerOutput>(output)
                return jsonOutput.result
            } catch (e: JsonException) {
                throw WebDriverException(
                    "Failed to parse json output, executed: $arguments\n$output",
                    e,
                )
            }
        }
    }
}
