package com.netpress.huck

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant

// Ports zouk's ScanEntrySpec.swift's grouping (Decodable / id / #downloadedAt and
// #formattedDate / #humanSize / #timeAgo(relativeTo:)) -- see docs/COMMENTS.md.
class ScanEntrySpec :
    DescribeSpec({
        describe("ScanEntry") {
            val name = "1779907271.pdf"
            val size = 79_992L
            val time = "2026-07-19T10:00:00Z"
            val path = "/download/1779907271.pdf"

            describe("Decodable") {
                context("when decoding a server JSON listing") {
                    it("decodes the name, size, time, and path fields") {
                        val json = """[{"name":"$name","size":$size,"time":"$time","path":"$path"}]"""

                        val scans = Json.decodeFromString<List<ScanEntry>>(json)

                        scans shouldHaveSize 1
                        scans[0].name shouldBe name
                        scans[0].size shouldBe size
                        scans[0].path shouldBe path
                        scans[0].downloadedAt.shouldNotBeNull()
                    }
                }
            }

            describe("id") {
                it("is the scan's name") {
                    ScanEntry(name = name, size = size, time = time, path = path).id shouldBe name
                }
            }

            describe("#downloadedAt and #formattedDate") {
                context("with a valid timestamp") {
                    it("are both non-null") {
                        val scan = ScanEntry(name = name, size = size, time = time, path = path)

                        scan.downloadedAt.shouldNotBeNull()
                        scan.formattedDate.shouldNotBeNull()
                    }
                }

                context("with an unparsable timestamp") {
                    it("are both null") {
                        val scan = ScanEntry(name = name, size = size, time = "invalid", path = path)

                        scan.downloadedAt should beNull()
                        scan.formattedDate should beNull()
                    }
                }
            }

            describe("#humanSize") {
                it("formats as \"80 KB\"") {
                    ScanEntry(name = name, size = size, time = time, path = path).humanSize shouldBe "80 KB"
                }
            }

            describe("#timeAgo(relativeTo:)") {
                context("with a valid timestamp") {
                    it("returns \"5 minutes ago\" for a 5-minute gap") {
                        val scan = ScanEntry(name = name, size = size, time = time, path = path)

                        scan.timeAgo(Instant.parse("2026-07-19T10:05:00Z")) shouldBe "5 minutes ago"
                    }
                }

                context("with an unparsable timestamp") {
                    it("falls back to the whenNil text instead of requiring the caller to guard") {
                        val scan = ScanEntry(name = name, size = size, time = "invalid", path = path)

                        scan.timeAgo(Instant.now()) shouldBe "an unknown time"
                    }
                }
            }
        }
    })
