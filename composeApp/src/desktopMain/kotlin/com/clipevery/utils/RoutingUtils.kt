package com.clipevery.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun successResponse(call: ApplicationCall) {
    call.respond(status = HttpStatusCode.OK, message = "")
}

suspend fun failResponse(call: ApplicationCall, message: String) {
    call.respond(status = HttpStatusCode.InternalServerError, message = message)
}