/*
 * YouTub Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/
package com.youtub.data.network

import com.youtub.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class YouTubeDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val okHttpRequest = buildOkHttpRequest(request)
        val url = request.url()

        val maxRetries = 2
        var attempt = 0
        var lastResponse: okhttp3.Response? = null
        var lastException: IOException? = null

        while (attempt <= maxRetries) {
            try {
                val okHttpResponse = client.newCall(okHttpRequest).execute()

                // If it's a 403 or 429, it might be an IP block or temporary throttle.
                if (okHttpResponse.code == 403 || okHttpResponse.code == 429) {
                    okHttpResponse.close()
                    attempt++
                    if (attempt <= maxRetries) {
                        Thread.sleep(1000L * attempt) // Linear backoff
                        continue
                    }
                }

                lastResponse = okHttpResponse
                break
            } catch (e: IOException) {
                lastException = e
                attempt++
                if (attempt <= maxRetries) {
                    Thread.sleep(500L * attempt)
                }
            }
        }

        val response = lastResponse ?: throw lastException ?: IOException("Request failed after $maxRetries retries")
        return toExtractorResponse(response, url)
    }

    @Throws(IOException::class)
    override fun executeAsync(request: Request, callback: Downloader.AsyncCallback): CancellableCall {
        val call = client.newCall(buildOkHttpRequest(request))
        val cancellableCall = CancellableCall(call)
        Thread {
            try {
                call.execute().use { okHttpResponse ->
                    val extractorResponse = toExtractorResponse(okHttpResponse, request.url())
                    cancellableCall.setFinished()
                    callback.onSuccess(extractorResponse)
                }
            } catch (e: Exception) {
                cancellableCall.setFinished()
                callback.onError(e)
            }
        }.start()
        return cancellableCall
    }

    private fun buildOkHttpRequest(request: Request): OkHttpRequest {
        val url = request.url()
        val method = request.httpMethod()
        val headers = request.headers()
        val data = request.dataToSend()

        val okHttpRequestBuilder = OkHttpRequest.Builder()
            .url(url)
            .method(method, data?.toRequestBody())
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("User-Agent", Constants.DEFAULT_USER_AGENT)

        val mergedHeaders = headers.toMutableMap()

        // Bypass YouTube Consent/GDPR redirection in Europe
        // Merge with existing cookies if present
        if (url.contains("youtube.com") || url.contains("googlevideo.com")) {
            val existingCookies = mergedHeaders["Cookie"] ?: emptyList()
            if (existingCookies.none { it.contains("CONSENT=YES") }) {
                val newCookies = existingCookies.toMutableList().apply {
                    add(Constants.YouTube.CONSENT_COOKIE)
                }
                mergedHeaders["Cookie"] = newCookies
            }
        }

        mergedHeaders.forEach { (key, values) ->
            values.forEach { value ->
                okHttpRequestBuilder.addHeader(key, value)
            }
        }

        return okHttpRequestBuilder.build()
    }

    private fun toExtractorResponse(response: okhttp3.Response, url: String): Response {
        val responseBody = response.body?.string()
        val responseCode = response.code
        val responseMessage = response.message
        val responseHeaders = response.headers.toMultimap()

        return Response(
            responseCode, responseMessage, responseHeaders,
            responseBody, responseBody?.toByteArray(), url
        )
    }
}
