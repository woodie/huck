package com.netpress.huck

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.time.Instant

class ScanEntrySpec :
    DescribeSpec({
        describe("ScanEntry") {
            var time = ""
            val subject = { ScanEntry(name = "scan.pdf", size = 79_992, time = time, path = "/files/scan.pdf") }

            context("id") {
                beforeEach { time = "2026-07-19T10:00:00Z" }

                it("is the scan's name, matching zouk's Identifiable conformance") {
                    subject().id shouldBe "scan.pdf"
                }
            }

            context("humanSize") {
                beforeEach { time = "2026-07-19T10:00:00Z" }

                it("delegates to Humane.humanSize") {
                    subject().humanSize shouldBe "80 KB"
                }
            }

            context("with a valid ISO-8601 time") {
                beforeEach { time = "2026-07-19T10:00:00Z" }

                it("parses downloadedAt") {
                    subject().downloadedAt shouldBe Instant.parse("2026-07-19T10:00:00Z")
                }

                it("formats a non-null formattedDate") {
                    subject().formattedDate.shouldNotBeNull()
                }

                it("reports timeAgo relative to a given instant") {
                    val relativeTo = Instant.parse("2026-07-19T10:05:00Z")
                    subject().timeAgo(relativeTo) shouldBe "5 minutes ago"
                }
            }

            context("with an unparseable time") {
                beforeEach { time = "not-a-timestamp" }

                it("has a null downloadedAt") {
                    subject().downloadedAt should beNull()
                }

                it("has a null formattedDate") {
                    subject().formattedDate should beNull()
                }

                it("falls back to the whenNil wording for timeAgo") {
                    subject().timeAgo(Instant.now()) shouldBe "an unknown time"
                }
            }
        }
    })
