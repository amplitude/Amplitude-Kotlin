package com.amplitude.core.utils

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.State
import com.amplitude.core.utilities.Connection
import com.amplitude.core.utilities.HttpClient
import com.amplitude.core.utilities.Response
import com.amplitude.core.utilities.SuccessResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection

@ExperimentalCoroutinesApi
fun testAmplitude(configuration: Configuration): Amplitude {
    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)
    return object : Amplitude(configuration, State(), testScope, testDispatcher, testDispatcher, testDispatcher, testDispatcher) {}
}

fun mockHTTPClient(response: Response) {
    mockkConstructor(HttpClient::class)
    val stream = ByteArrayInputStream(
        "".trimIndent().toByteArray()
    )
    val httpConnection: HttpURLConnection = mockk()
    val connection = object : Connection(httpConnection, stream, null) {
    }
    connection.response = response
    every { anyConstructed<HttpClient>().upload() } returns connection
}

fun createSuccessResponse(): SuccessResponse {
    return SuccessResponse()
}
