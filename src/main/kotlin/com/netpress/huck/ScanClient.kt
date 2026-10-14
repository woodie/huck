package com.netpress.huck

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

class ScanClientError(
    message: String,
    // The HTTP status, when the server answered with one (404 on delete means already gone).
    val status: Int? = null,
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
            val result = httpClient.propfind(folderUrl(baseUrl))
            if (result.statusCode == 405) return@withContext fetchLegacyListing()
            checkOk(result.statusCode)
            WebDavListing.entries(result.body).sortedByDescending { it.downloadedAt ?: Instant.MIN }
        }

    // Until lambada#21 retires it, lambada-web answers PROPFIND with 405 but still serves files.json.
    private fun fetchLegacyListing(): List<ScanEntry> =
        try {
            val result = httpClient.get(baseUrl.resolve("files.json"))
            checkOk(result.statusCode)
            json.decodeFromString<List<ScanEntry>>(result.body.decodeToString())
        } catch (e: Exception) {
            throw ScanClientError(NO_WEBDAV_MESSAGE)
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

    // DELETE on the file's own WebDAV address, the same one GET downloads from.
    override suspend fun delete(scan: ScanEntry) {
        withContext(Dispatchers.IO) {
            val result = httpClient.delete(baseUrl.resolve(scan.path))
            checkOk(result.statusCode)
        }
    }

    // PUT into the folder under the file's own name, the way Explorer and lambada's web page upload.
    override suspend fun upload(file: File) {
        withContext(Dispatchers.IO) {
            val result = httpClient.put(uploadUrl(file.name, baseUrl), file)
            checkOk(result.statusCode)
        }
    }

    private fun checkOk(status: Int) {
        if (status !in 200..299) throw ScanClientError("The server responded with status $status.", status)
    }

    companion object {
        // lambada's nginx refuses bigger uploads (client_max_body_size 25m).
        const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024

        const val NO_WEBDAV_MESSAGE = "The server doesn't offer a WebDAV listing at that address (status 405)."

        // PROPFIND on a folder without its trailing slash gets redirected, and huck doesn't follow redirects.
        fun folderUrl(url: URI): URI = if (url.toString().endsWith("/")) url else URI("$url/")

        // URI's multi-argument constructor quotes what a path can't hold (space, #, ?, %), and
        // toASCIIString() encodes anything non-ASCII, so any file name stays one path segment.
        fun uploadUrl(
            name: String,
            baseUrl: URI,
        ): URI {
            val folder = folderUrl(baseUrl)
            return URI(URI(folder.scheme, folder.authority, folder.path + name, null, null).toASCIIString())
        }

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
