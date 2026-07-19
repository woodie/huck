package com.netpress.huck

import com.netpress.humane.Humane
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Ports zouk's ScanEntry (Sources/ZoukKit/ScanEntry.swift) -- see docs/COMMENTS.md
// for what's simplified here (formattedDate has no "Today"/"Yesterday" relative
// wording, unlike Foundation's DateFormatter.doesRelativeDateFormatting).
@Serializable
data class ScanEntry(
    val name: String,
    val size: Long,
    val time: String,
    val path: String,
) {
    val id: String get() = name

    val downloadedAt: Instant? by lazy { runCatching { Instant.parse(time) }.getOrNull() }

    val humanSize: String get() = Humane.humanSize(size)

    val formattedDate: String?
        get() {
            val at = downloadedAt ?: return null
            val formatter =
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(Locale.getDefault())
            return formatter.format(at.atZone(ZoneId.systemDefault()))
        }

    fun timeAgo(relativeTo: Instant): String = Humane.distanceInTime(downloadedAt, relativeTo, whenNil = "an unknown time")
}
