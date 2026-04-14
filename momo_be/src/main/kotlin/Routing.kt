package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.event.*


fun Application.configureRouting() {
    routing {
        // Health check
        get("/") {
            call.respondText("Server is running! 🚀", ContentType.Text.Plain)
        }

        get("/health") {
            call.respondText("OK", ContentType.Text.Plain, HttpStatusCode.OK)
        }

        // Test JSON parsing
        post("/test-json") {
            try {
                val body = call.receiveText()
                call.respondText("Received: $body", ContentType.Text.Plain, HttpStatusCode.OK)
            } catch (e: Exception) {
                call.respondText("Error: ${e.message}", ContentType.Text.Plain, HttpStatusCode.BadRequest)
            }
        }
        momoRoutes()

    }
}
