package com.clipevery.platform

expect fun currentPlatform(): Platform

interface Platform {
    val name: String
    val version: String
}
