package com.crosspaste.test

import org.junit.jupiter.api.Tag

/**
 * Marks slow integration-style suites (real child processes, full pairing
 * handshakes, real sockets and wall-clock timing) that dominate suite runtime.
 *
 * Tagged suites are excluded from the default fast tier that runs on every PR
 * (`./gradlew app:desktopTest`); they run when `-PintegrationTests` is passed,
 * which release and beta builds do. Run the full suite locally with:
 * `./gradlew app:desktopTest -PintegrationTests`.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("integration")
annotation class IntegrationTest
