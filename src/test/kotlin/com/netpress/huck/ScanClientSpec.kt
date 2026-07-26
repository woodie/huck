package com.netpress.huck

import com.netpress.kwick.justBeforeEach
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
// differs). fetchScans()/delete(_:) still do their own setup inline inside each it's own runTest
// block -- zouk's own spec doesn't hoist those acts into justBeforeEach either, since each it
// there asserts a single fact rather than sharing one result across several. cachedFile()/
// save()/uniqueDestination() do hoist their act via kwick's justBeforeEach, matching zouk's real
// justBeforeEach usage at those three call sites -- kwick's justBeforeEach takes a
// suspend TestScope.() -> Unit block natively, so no separate coroutine-test listener extension
// is needed the way kotidy's docs/FRAMEWORK.md once flagged as a gap (see kwick's issue #7).
class ScanClientSpec :
    DescribeSpec({
        val name = "1779907271.pdf"
        val size = 7L
        val time = "2026-06-25T10:30:00-07:00"
        val path = "/download/$name"
        val baseUrl = URI("http://scans.example.com")
        val scan = ScanEntry(name = name, size = size, time = time, path = path)

        fun tempDirectory(): File = Files.createTempDirectory("huck-tests-").toFile()

        fun tempFileContaining(bytes: ByteArray): File = Files.createTempFile("huck-tests-", ".tmp").toFile().also { it.writeBytes(bytes) }

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
                lateinit var fakeHttp: FakeScanHttpClient
                lateinit var client: ScanClient
                lateinit var cacheDirectory: File
                lateinit var local: File

                beforeEach {
                    fakeHttp = FakeScanHttpClient()
                    client = ScanClient(baseUrl, fakeHttp)
                    cacheDirectory = tempDirectory()
                }
                afterEach { cacheDirectory.deleteRecursively() }

                justBeforeEach { local = client.cachedFile(scan, cacheDirectory) }

                context("when the file isn't cached yet") {
                    val bytes = "pdf bytes".toByteArray()
                    lateinit var requestedUrl: URI

                    beforeEach {
                        fakeHttp.downloadHandler = { url ->
                            requestedUrl = url
                            DownloadResult(200, tempFileContaining(bytes))
                        }
                    }

                    it("downloads from scan.path resolved against baseURL") {
                        requestedUrl shouldBe URI("http://scans.example.com/download/$name")
                    }

                    it("saves the downloaded bytes under the scan's name in cacheDirectory") {
                        local shouldBe File(cacheDirectory, name)
                        local.readBytes().toList() shouldBe bytes.toList()
                    }
                }

                context("when the file is already cached and its size matches scan.size") {
                    // 7 bytes, matches scan.size -- cachedFile should trust the cache.
                    val existingBytes = "is-here".toByteArray()

                    beforeEach {
                        File(cacheDirectory, name).writeBytes(existingBytes)
                        // Tripwire: fails the test if the short-circuit logic ever regresses.
                        fakeHttp.downloadHandler = { error("should not download") }
                    }

                    it("returns the already-cached file without downloading again") {
                        local.readBytes().toList() shouldBe existingBytes.toList()
                    }
                }

                context("when a same-named file is cached but its size doesn't match scan.size") {
                    // Regression test for the stale-cache bug ScanClient.cachedFile fixed.
                    val staleBytes = "stale, wrong file entirely".toByteArray()
                    val freshBytes = "pdf bytes".toByteArray()
                    lateinit var requestedUrl: URI

                    beforeEach {
                        File(cacheDirectory, name).writeBytes(staleBytes)
                        fakeHttp.downloadHandler = { url ->
                            requestedUrl = url
                            DownloadResult(200, tempFileContaining(freshBytes))
                        }
                    }

                    it("re-downloads from scan.path instead of trusting the stale cache") {
                        requestedUrl shouldBe URI("http://scans.example.com/download/$name")
                    }

                    it("overwrites the cached file with the freshly downloaded bytes") {
                        local.readBytes().toList() shouldBe freshBytes.toList()
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
                val bytes = "pdf bytes".toByteArray()
                lateinit var root: File
                lateinit var client: ScanClient
                lateinit var cacheDirectory: File
                lateinit var destination: File
                lateinit var saved: File

                beforeEach {
                    root = tempDirectory()
                    cacheDirectory = File(root, "cache")
                    val destinationDirectory = File(root, "Downloads").also { it.mkdirs() }
                    destination = File(destinationDirectory, name)
                    val fakeHttp = FakeScanHttpClient(downloadHandler = { DownloadResult(200, tempFileContaining(bytes)) })
                    client = ScanClient(baseUrl, fakeHttp)
                }
                afterEach { root.deleteRecursively() }

                justBeforeEach { saved = client.save(scan, destination, cacheDirectory) }

                context("when destination has no existing file") {
                    it("returns destination") {
                        saved shouldBe destination
                    }

                    it("copies the cached scan's bytes to destination") {
                        destination.readBytes().toList() shouldBe bytes.toList()
                    }
                }

                context("when destination already has a different file") {
                    beforeEach { destination.writeBytes("stale".toByteArray()) }

                    it("overwrites it with the cached scan's bytes") {
                        destination.readBytes().toList() shouldBe bytes.toList()
                    }
                }
            }

            describe("#uniqueDestination(for:in:)") {
                lateinit var directory: File
                lateinit var destination: File

                beforeEach { directory = tempDirectory() }
                afterEach { directory.deleteRecursively() }

                justBeforeEach { destination = ScanClient.uniqueDestination("scan.pdf", directory) }

                context("when nothing exists at that name yet") {
                    it("returns the name unchanged") {
                        destination shouldBe File(directory, "scan.pdf")
                    }
                }

                context("when \"scan.pdf\" already exists") {
                    beforeEach { File(directory, "scan.pdf").writeBytes(ByteArray(0)) }

                    it("returns \"scan (1).pdf\"") {
                        destination shouldBe File(directory, "scan (1).pdf")
                    }

                    context("and \"scan (1).pdf\" also already exists") {
                        beforeEach { File(directory, "scan (1).pdf").writeBytes(ByteArray(0)) }

                        it("returns \"scan (2).pdf\"") {
                            destination shouldBe File(directory, "scan (2).pdf")
                        }
                    }
                }
            }
        }
    })
