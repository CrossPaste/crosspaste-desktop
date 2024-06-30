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

import org.openqa.selenium.internal.Require
import org.openqa.selenium.json.JsonInput
import java.util.Locale
import java.util.Objects
import java.util.logging.Level

class SeleniumManagerOutput {
    var logs: List<Log>? = null
    var result: Result? = null

    class Log(level: Level, val timestamp: Long, message: String) {
        val level: Level
        val message: String

        init {
            this.level = Require.nonNull("level", level)
            this.message = Require.nonNull("message", message)
        }

        companion object {
            private fun fromJson(input: JsonInput): Log {
                var level = Level.FINE
                var timestamp = System.currentTimeMillis()
                var message = ""

                input.beginObject()
                while (input.hasNext()) {
                    when (input.nextName()) {
                        "level" ->
                            level =
                                when (input.nextString().lowercase(Locale.getDefault())) {
                                    "error", "warn" -> Level.WARNING
                                    "info" -> Level.INFO
                                    else -> Level.FINE
                                }

                        "timestamp" -> timestamp = input.nextNumber().toLong()
                        "message" -> message = input.nextString()
                    }
                }
                input.endObject()

                return Log(level, timestamp, message)
            }
        }
    }

    class Result(val code: Int, val message: String?, val driverPath: String?, val browserPath: String?) {
        constructor(driverPath: String?) : this(0, null, driverPath, null)

        override fun toString(): String {
            return (
                "Result{" +
                    "code=" +
                    code +
                    ", message='" +
                    message +
                    '\'' +
                    ", driverPath='" +
                    driverPath +
                    '\'' +
                    ", browserPath='" +
                    browserPath +
                    '\'' +
                    '}'
            )
        }

        override fun equals(o: Any?): Boolean {
            if (o !is Result) {
                return false
            }
            val that = o
            return code == that.code && message == that.message && driverPath == that.driverPath && browserPath == that.browserPath
        }

        override fun hashCode(): Int {
            return Objects.hash(code, message, driverPath, browserPath)
        }

        companion object {
            private fun fromJson(input: JsonInput): Result {
                var code = 0
                var message: String? = null
                var driverPath: String? = null
                var browserPath: String? = null

                input.beginObject()
                while (input.hasNext()) {
                    when (input.nextName()) {
                        "code" -> code = input.read(Int::class.java)
                        "message" -> message = input.read(String::class.java)
                        "driver_path" -> driverPath = input.read(String::class.java)
                        "browser_path" -> browserPath = input.read(String::class.java)
                        else -> input.skipValue()
                    }
                }
                input.endObject()

                return Result(code, message, driverPath, browserPath)
            }
        }
    }
}
