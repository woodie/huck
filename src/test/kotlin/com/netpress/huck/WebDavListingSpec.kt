package com.netpress.huck

import com.netpress.kwick.justBeforeEach
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// Ports zouk's WebDAVListingSpec.swift.
class WebDavListingSpec :
    DescribeSpec({
        describe("WebDavListing.entries(body)") {
            lateinit var xml: String
            var entries: List<ScanEntry> = emptyList()

            justBeforeEach { entries = runCatching { WebDavListing.entries(xml.toByteArray()) }.getOrDefault(emptyList()) }

            context("with the reply nginx gave on the Pi") {
                beforeEach {
                    xml =
                        """
                        <?xml version="1.0" encoding="utf-8" ?>
                        <D:multistatus xmlns:D="DAV:">
                        <D:response>
                        <D:href>/scans/</D:href>
                        <D:propstat>
                        <D:prop>
                        <D:displayname>scans</D:displayname>
                        <D:getlastmodified>Tue, 13 Oct 2026 00:38:31 GMT</D:getlastmodified>
                        <D:resourcetype><D:collection/></D:resourcetype>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                        </D:propstat>
                        </D:response>
                        <D:response>
                        <D:href>/scans/1791851911.pdf</D:href>
                        <D:propstat>
                        <D:prop>
                        <D:displayname>1791851911.pdf</D:displayname>
                        <D:getcontentlength>252184</D:getcontentlength>
                        <D:getlastmodified>Tue, 13 Oct 2026 00:38:33 GMT</D:getlastmodified>
                        <D:resourcetype></D:resourcetype>
                        </D:prop>
                        <D:status>HTTP/1.1 200 OK</D:status>
                        </D:propstat>
                        </D:response>
                        </D:multistatus>
                        """.trimIndent()
                }

                it("skips the folder itself") {
                    entries.size shouldBe 1
                }

                it("reads the file's name, size and address") {
                    entries.first().name shouldBe "1791851911.pdf"
                    entries.first().size shouldBe 252_184L
                    entries.first().path shouldBe "/scans/1791851911.pdf"
                }

                it("keeps the modified date as ISO 8601") {
                    entries.first().time shouldBe "2026-10-13T00:38:33Z"
                }
            }

            context("with a different namespace prefix, a self-closing resourcetype and an encoded address") {
                beforeEach {
                    xml =
                        """
                        <?xml version="1.0"?>
                        <d:multistatus xmlns:d="DAV:">
                        <d:response>
                        <d:href>/scan%20with%20spaces.pdf</d:href>
                        <d:propstat><d:prop>
                        <d:displayname>scan with spaces.pdf</d:displayname>
                        <d:getcontentlength>7</d:getcontentlength>
                        <d:getlastmodified>Mon, 12 Oct 2026 21:48:35 GMT</d:getlastmodified>
                        <d:resourcetype/>
                        </d:prop></d:propstat>
                        </d:response>
                        </d:multistatus>
                        """.trimIndent()
                }

                it("uses the plain name for display and the encoded address for requests") {
                    entries.first().name shouldBe "scan with spaces.pdf"
                    entries.first().path shouldBe "/scan%20with%20spaces.pdf"
                }
            }

            context("when an entry has no displayname") {
                beforeEach {
                    xml =
                        """
                        <D:multistatus xmlns:D="DAV:"><D:response>
                        <D:href>/scans/scan%20one.pdf</D:href>
                        <D:propstat><D:prop><D:getcontentlength>1</D:getcontentlength><D:resourcetype/></D:prop></D:propstat>
                        </D:response></D:multistatus>
                        """.trimIndent()
                }

                it("takes the name from the address") {
                    entries.first().name shouldBe "scan one.pdf"
                }
            }

            context("when an entry's modified date can't be read") {
                beforeEach {
                    xml =
                        """
                        <D:multistatus xmlns:D="DAV:"><D:response>
                        <D:href>/a.pdf</D:href>
                        <D:propstat><D:prop><D:getlastmodified>yesterday-ish</D:getlastmodified><D:resourcetype/></D:prop></D:propstat>
                        </D:response></D:multistatus>
                        """.trimIndent()
                }

                it("leaves the time empty, so the entry has no date") {
                    entries.first().time shouldBe ""
                    entries.first().downloadedAt shouldBe null
                }
            }

            context("when the reply isn't XML") {
                beforeEach { xml = "<html><body>405 Not Allowed" }

                it("throws ScanClientError") {
                    shouldThrow<ScanClientError> { WebDavListing.entries(xml.toByteArray()) }
                }
            }
        }
    })
