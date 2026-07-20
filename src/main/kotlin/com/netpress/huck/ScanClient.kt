package com.netpress.huck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class ScanClientError(
    message: String,
) : Exception(message)

// Ports zouk's ScanClient (Sources/ZoukKit/ScanClient.swift) onto java.net.http.HttpClient --
// no separate HTTP library dependency, matching this account's "stdlib first" posture in
// Go/Ruby. See docs/COMMENTS.md for the cachedFile robustness gap (direct-to-destination
// write here, vs. Swift's download-to-temp-then-move).
class ScanClient(
    private val baseUrl: URI,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : ScanFetching {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchScans(): List<ScanEntry> =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(baseUrl.resolve("files.json")).GET().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            checkOk(response.statusCode())
            json.decodeFromString(response.body())
        }

    // Re-downloads on a scan.size mismatch instead of trusting a stale same-named cache entry.
    override suspend fun cachedFile(
        scan: ScanEntry,
        cacheDirectory: File,
    ): File =
        withContext(Dispatchers.IO) {
            val local = File(cacheDirectory, scan.name)
            if (local.exists() && local.length() == scan.size) return@withContext local

            cacheDirectory.mkdirs()
            val request = HttpRequest.newBuilder(baseUrl.resolve(scan.path)).GET().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(local.toPath()))
            checkOk(response.statusCode())
            local
        }

    override suspend fun save(
        scan: ScanEntry,
        destination: File,
        cacheDirectory: File,
    ): File =
        withContext(Dispatchers.IO) {
            val cached = cachedFile(scan, cacheDirectory)
            cached.copyTo(destination, overwrite = true)
            destination
        }

    // DELETE on the same path GET uses to download; lambada-web shares one route for both verbs.
    override suspend fun delete(scan: ScanEntry) {
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder(baseUrl.resolve(scan.path)).DELETE().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            checkOk(response.statusCode())
        }
    }

    private fun checkOk(status: Int) {
        if (status !in 200..299) throw ScanClientError("The server responded with status $status.")
    }

    companion object {
        // Finder-style de-dup naming: "scan.pdf" -> "scan (1).pdf" instead of overwriting.
        fun uniqueDestination(
            filename: String,
            directory: File,
        ): File {
            val dot = filename.lastIndexOf('.')
            val base = if (dot >= 0) filename.substring(0, dot) else filename
            val ext = if (dot >= 0) filename.substring(dot + 1) else ""

            var candidate = File(directory, filename)
            var counter = 1
            while (candidate.exists()) {
                val suffixed = if (ext.isEmpty()) "$base ($counter)" else "$base ($counter).$ext"
                candidate = File(directory, suffixed)
                counter++
            }
            return candidate
        }
    }
}
