package com.clipevery.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

class DesktopClipClient: ClipClient {

    val client = HttpClient(CIO)

}
