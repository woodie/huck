package com.netpress.huck

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration

class HttpResult(
    val statusCode: Int,
    val body: ByteArray,
)

class DownloadResult(
    val statusCode: Int,
    val tempFile: File,
)

// Test seam matching zouk's ScanHTTPClient protocol -- lets ScanClientSpec fake server
// responses (status codes, bodies) without a real network call. JdkHttpScanHttpClient below is
// the real implementation, backed by java.net.http.HttpClient, matching zouk's own
// URLSession: ScanHTTPClient conformance. One method per HTTP verb ScanClient actually needs
// (get/download/delete), rather than mechanically porting URLSession's own three
// (data(from:)/download(from:)/data(for:)) -- java.net.http.HttpClient's single generic
// send(request, bodyHandler) doesn't map onto that shape as directly, and a verb-per-method
// interface means the DELETE verb is guaranteed correct by construction rather than something
// a test has to separately check.
interface ScanHttpClient {
    fun get(url: URI): HttpResult

    // Downloads to its own temp file rather than streaming straight to a caller-provided
    // destination -- matches zouk's own URLSession.download(from:)/ScanHTTPClient.download(from:)
    // shape, where the caller (ScanClient.cachedFile) moves the temp file into place itself.
    // Fixes the "direct-to-destination write" gap docs/COMMENTS.md flagged (no temp-then-move,
    // so a crash/interrupt mid-download could leave a corrupt cached file behind) as a side
    // effect of adopting this shape, not a separate change.
    fun download(url: URI): DownloadResult

    fun delete(url: URI): HttpResult
}

class JdkHttpScanHttpClient(
    // connectTimeout() here plus REQUEST_TIMEOUT on every request below -- HttpClient.
    // newHttpClient()'s own defaults have NO timeout at all on either front. Confirmed as a real
    // gap on a real run: a delete() whose DELETE request never completed left AppModel.isBusy
    // stuck true forever (nothing ever reached the catch/finally to reset it), which disabled
    // the refresh button and generally "locked" the app until it was force-restarted. Without a
    // timeout, a hung connection or an unresponsive server suspends the calling coroutine
    // indefinitely instead of throwing -- there's no other path back to a usable UI state.
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build(),
) : ScanHttpClient {
    override fun get(url: URI): HttpResult {
        val request =
            HttpRequest
                .newBuilder(url)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        return HttpResult(response.statusCode(), response.body())
    }

    override fun download(url: URI): DownloadResult {
        val tempFile = Files.createTempFile("huck-download-", ".tmp").toFile()
        val request =
            HttpRequest
                .newBuilder(url)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempFile.toPath()))
        return DownloadResult(response.statusCode(), tempFile)
    }

    override fun delete(url: URI): HttpResult {
        val request =
            HttpRequest
                .newBuilder(url)
                .timeout(REQUEST_TIMEOUT)
                .DELETE()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        return HttpResult(response.statusCode(), response.body())
    }

    companion object {
        // Generous enough for a real file download over a slow local network, not just a
        // files.json/DELETE round-trip -- one constant for all three request kinds rather than
        // tuning each separately, matching this file's existing "keep it simple" posture.
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
