package com.netpress.huck

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.nio.file.Files

// Ports zouk's ScanClientSpec.swift, enabled by the ScanHttpClient seam ScanHttpClient.kt adds
// (matching zouk's own ScanHTTPClient protocol -- see that file's comments for why the shape
// differs). fetchScans/cachedFile/delete/save all go through suspend fun ScanClient methods, so
// each it wraps its own runTest and does its own setup inline, matching AppModelSpec.kt's
// established pattern in this file rather than Kotest's beforeEach -- mixing beforeEach with
// suspend setup needs a coroutine-test listener extension this project doesn't have configured.
// uniqueDestination(for:in:) is a plain function, so its own describe block below uses real
// beforeEach/afterEach instead, matching the account's usual "Test structure" convention where
// there's no such constraint.
class ScanClientSpec : DescribeSpec({
    val name = "1779907271.pdf"
    val size = 7L
    val time = "2026-06-25T10:30:00-07:00"
    val path = "/download/$name"
    val baseUrl = URI("http://scans.example.com")
    val scan = ScanEntry(name = name, size = size, time = time, path = path)

    fun tempDirectory(): File = Files.createTempDirectory("huck-tests-").toFile()

    fun tempFileContaining(bytes: ByteArray): File =
        Files.createTempFile("huck-tests-", ".tmp").toFile().also { it.writeBytes(bytes) }

    describe("ScanClient") {
        describe("#fetchScans()") {
            context("when the server responds with 200 and a valid listing") {
                it("requests files.json under baseURL and decodes the scans the server returns") {
                    runTest {
                        var requestedUrl: URI? = null
                        val body = Json.encodeToString(listOf(scan)).toByteArray()
                        val fakeHttp =
                            FakeScanHttpClient(getHandler = { url ->
                                requestedUrl = url
                                HttpResult(200, body)
                            })
                        val client = ScanClient(baseUrl, fakeHttp)

                        val scans = client.fetchScans()

                        requestedUrl shouldBe URI("http://scans.example.com/files.json")
                        scans shouldBe listOf(scan)
                    }
                }
            }

            context("when the server responds with a non-2xx status") {
                it("throws ScanClientError with that status code") {
                    runTest {
                        val fakeHttp = FakeScanHttpClient(getHandler = { HttpResult(500, ByteArray(0)) })
                        val client = ScanClient(baseUrl, fakeHttp)

                        val error = shouldThrow<ScanClientError> { client.fetchScans() }

                        error.message shouldBe "The server responded with status 500."
                    }
                }
            }
        }

        describe("#cachedFile(for:in:)") {
            context("when the file isn't cached yet") {
                it("downloads from scan.path resolved against baseURL, saved under the scan's name") {
                    runTest {
                        val cacheDirectory = tempDirectory()
                        try {
                            var requestedUrl: URI? = null
                            val bytes = "pdf bytes".toByteArray()
                            val fakeHttp =
                                FakeScanHttpClient(downloadHandler = { url ->
                                    requestedUrl = url
                                    DownloadResult(200, tempFileContaining(bytes))
                                })
                            val client = ScanClient(baseUrl, fakeHttp)

                            val local = client.cachedFile(scan, cacheDirectory)

                            requestedUrl shouldBe URI("http://scans.example.com/download/$name")
                            local shouldBe File(cacheDirectory, name)
                            local.readBytes().toList() shouldBe bytes.toList()
                        } finally {
                            cacheDirectory.deleteRecursively()
                        }
                    }
                }
            }

            context("when the file is already cached and its size matches scan.size") {
                it("returns the already-cached file without downloading again") {
                    runTest {
                        val cacheDirectory = tempDirectory()
                        try {
                            // 7 bytes, matches scan.size -- cachedFile should trust the cache.
                            val existingBytes = "is-here".toByteArray()
                            File(cacheDirectory, name).writeBytes(existingBytes)
                            // Tripwire: fails the test if the short-circuit logic ever regresses.
                            val fakeHttp = FakeScanHttpClient(downloadHandler = { error("should not download") })
                            val client = ScanClient(baseUrl, fakeHttp)

                            val local = client.cachedFile(scan, cacheDirectory)

                            local.readBytes().toList() shouldBe existingBytes.toList()
                        } finally {
                            cacheDirectory.deleteRecursively()
                        }
                    }
                }
            }

            context("when a same-named file is cached but its size doesn't match scan.size") {
                // Regression test for the stale-cache bug ScanClient.cachedFile fixed.
                it("re-downloads from scan.path instead of trusting the stale cache, overwriting it") {
                    runTest {
                        val cacheDirectory = tempDirectory()
                        try {
                            val staleBytes = "stale, wrong file entirely".toByteArray()
                            File(cacheDirectory, name).writeBytes(staleBytes)
                            var requestedUrl: URI? = null
                            val freshBytes = "pdf bytes".toByteArray()
                            val fakeHttp =
                                FakeScanHttpClient(downloadHandler = { url ->
                                    requestedUrl = url
                                    DownloadResult(200, tempFileContaining(freshBytes))
                                })
                            val client = ScanClient(baseUrl, fakeHttp)

                            val local = client.cachedFile(scan, cacheDirectory)

                            requestedUrl shouldBe URI("http://scans.example.com/download/$name")
                            local.readBytes().toList() shouldBe freshBytes.toList()
                        } finally {
                            cacheDirectory.deleteRecursively()
                        }
                    }
                }
            }
        }

        describe("#delete(_:)") {
            context("when the server responds with 204") {
                it("sends DELETE to scan.path resolved against baseURL") {
                    runTest {
                        var requestedUrl: URI? = null
                        val fakeHttp =
                            FakeScanHttpClient(deleteHandler = { url ->
                                requestedUrl = url
                                HttpResult(204, ByteArray(0))
                            })
                        val client = ScanClient(baseUrl, fakeHttp)

                        client.delete(scan)

                        requestedUrl shouldBe URI("http://scans.example.com/download/$name")
                    }
                }
            }

            context("when the server responds with a non-2xx status") {
                it("throws ScanClientError with that status code") {
                    runTest {
                        val fakeHttp = FakeScanHttpClient(deleteHandler = { HttpResult(404, ByteArray(0)) })
                        val client = ScanClient(baseUrl, fakeHttp)

                        val error = shouldThrow<ScanClientError> { client.delete(scan) }

                        error.message shouldBe "The server responded with status 404."
                    }
                }
            }
        }

        describe("#save(_:to:cacheDirectory:)") {
            // Matches zouk's own setup: a fresh client/cacheDirectory/destination per it, not
            // shared -- freshSetup() stands in for beforeEach here (see the class-level comment).
            fun freshSetup(bytes: ByteArray): Triple<ScanClient, File, File> {
                val root = tempDirectory()
                val cacheDirectory = File(root, "cache")
                val destinationDirectory = File(root, "Downloads").also { it.mkdirs() }
                val destination = File(destinationDirectory, name)
                val fakeHttp = FakeScanHttpClient(downloadHandler = { DownloadResult(200, tempFileContaining(bytes)) })
                return Triple(ScanClient(baseUrl, fakeHttp), cacheDirectory, destination)
            }

            context("when destination has no existing file") {
                it("returns destination and copies the cached scan's bytes there") {
                    runTest {
                        val bytes = "pdf bytes".toByteArray()
                        val (client, cacheDirectory, destination) = freshSetup(bytes)
                        try {
                            val saved = client.save(scan, destination, cacheDirectory)

                            saved shouldBe destination
                            destination.readBytes().toList() shouldBe bytes.toList()
                        } finally {
                            destination.parentFile.parentFile.deleteRecursively()
                        }
                    }
                }
            }

            context("when destination already has a different file") {
                it("overwrites it with the cached scan's bytes") {
                    runTest {
                        val bytes = "pdf bytes".toByteArray()
                        val (client, cacheDirectory, destination) = freshSetup(bytes)
                        destination.writeBytes("stale".toByteArray())
                        try {
                            client.save(scan, destination, cacheDirectory)

                            destination.readBytes().toList() shouldBe bytes.toList()
                        } finally {
                            destination.parentFile.parentFile.deleteRecursively()
                        }
                    }
                }
            }
        }

        describe("#uniqueDestination(for:in:)") {
            lateinit var directory: File

            beforeEach { directory = tempDirectory() }
            afterEach { directory.deleteRecursively() }

            context("when nothing exists at that name yet") {
                it("returns the name unchanged") {
                    ScanClient.uniqueDestination("scan.pdf", directory) shouldBe File(directory, "scan.pdf")
                }
            }

            context("when \"scan.pdf\" already exists") {
                beforeEach { File(directory, "scan.pdf").writeBytes(ByteArray(0)) }

                it("returns \"scan (1).pdf\"") {
                    ScanClient.uniqueDestination("scan.pdf", directory) shouldBe File(directory, "scan (1).pdf")
                }

                context("and \"scan (1).pdf\" also already exists") {
                    beforeEach { File(directory, "scan (1).pdf").writeBytes(ByteArray(0)) }

                    it("returns \"scan (2).pdf\"") {
                        ScanClient.uniqueDestination("scan.pdf", directory) shouldBe File(directory, "scan (2).pdf")
                    }
                }
            }
        }
    }
})
