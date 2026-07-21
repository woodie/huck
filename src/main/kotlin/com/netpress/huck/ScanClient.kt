package com.netpress.huck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ScanClientError(
    message: String,
) : Exception(message)

// Ports zouk's ScanClient (Sources/ZoukKit/ScanClient.swift). httpClient is the same
// testability seam zouk's own ScanHTTPClient protocol is -- see ScanHttpClient.kt.
class ScanClient(
    private val baseUrl: URI,
    private val httpClient: ScanHttpClient = JdkHttpScanHttpClient(),
) : ScanFetching {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun fetchScans(): List<ScanEntry> =
        withContext(Dispatchers.IO) {
            val result = httpClient.get(baseUrl.resolve("files.json"))
            checkOk(result.statusCode)
            json.decodeFromString(result.body.decodeToString())
        }

    // Re-downloads on a scan.size mismatch instead of trusting a stale same-named cache entry.
    override suspend fun cachedFile(
        scan: ScanEntry,
        cacheDirectory: File,
    ): File =
        withContext(Dispatchers.IO) {
            val local = File(cacheDirectory, scan.name)
            if (local.exists() && local.length() == scan.size) return@withContext local

            val result = httpClient.download(baseUrl.resolve(scan.path))
            checkOk(result.statusCode)

            cacheDirectory.mkdirs()
            Files.move(result.tempFile.toPath(), local.toPath(), StandardCopyOption.REPLACE_EXISTING)
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
            val result = httpClient.delete(baseUrl.resolve(scan.path))
            checkOk(result.statusCode)
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
