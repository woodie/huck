package com.netpress.huck

import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.net.URI
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

// Reads a WebDAV PROPFIND (Depth: 1) reply into ScanEntry values, skipping folders.
object WebDavListing {
    private const val DAV = "DAV:"

    fun entries(body: ByteArray): List<ScanEntry> {
        val document =
            try {
                newBuilderFactory()
                    .newDocumentBuilder()
                    .apply { setErrorHandler(QuietErrors) }
                    .parse(ByteArrayInputStream(body))
            } catch (e: SAXException) {
                throw ScanClientError("The server's listing couldn't be read.")
            }
        val responses = document.getElementsByTagNameNS(DAV, "response")
        return (0 until responses.length).mapNotNull { entry(responses.item(it) as Element) }
    }

    // WebDAV dates are HTTP dates ("Tue, 13 Oct 2026 00:38:33 GMT"); ScanEntry keeps ISO 8601.
    fun iso8601FromHttpDate(value: String): String? =
        runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toString()
        }.getOrNull()

    private fun entry(response: Element): ScanEntry? {
        if (response.getElementsByTagNameNS(DAV, "collection").length > 0) return null
        val href = text(response, "href") ?: return null
        val name = text(response, "displayname") ?: nameFromHref(href)
        val size = text(response, "getcontentlength")?.toLongOrNull() ?: 0L
        val time = text(response, "getlastmodified")?.let(::iso8601FromHttpDate) ?: ""
        return ScanEntry(name = name, size = size, time = time, path = href)
    }

    private fun text(
        parent: Element,
        localName: String,
    ): String? =
        parent
            .getElementsByTagNameNS(DAV, localName)
            .item(0)
            ?.textContent
            ?.trim()
            ?.ifEmpty { null }

    private fun nameFromHref(href: String): String {
        val path = runCatching { URI(href).path }.getOrNull() ?: href
        return path.trimEnd('/').substringAfterLast('/')
    }

    // Namespace-aware so any DAV: prefix (D:, d:) works; no DOCTYPEs, so no external entities.
    private fun newBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }

    // The default handler prints "[Fatal Error]" to stderr before throwing; this only throws.
    private object QuietErrors : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit

        override fun error(exception: SAXParseException): Unit = throw exception

        override fun fatalError(exception: SAXParseException): Unit = throw exception
    }
}
