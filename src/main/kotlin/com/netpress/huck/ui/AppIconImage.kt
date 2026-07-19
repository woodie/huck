package com.netpress.huck.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.netpress.huck.resources.Res
import com.netpress.huck.resources.small
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Ports zouk's AppIconImage (Sources/ZoukKit/AppIconImage.swift). Swift loads AppIcon.png from
// disk at runtime and falls back to a system glyph if it's missing; that fallback isn't ported
// here because Compose Resources resolves the DrawableResource at compile time -- a missing PNG
// is a build failure, not a runtime condition, so there's nothing to fall back to. See
// docs/COMMENTS.md. Swift also rounds corners to 22% of the shorter side via GeometryReader;
// RoundedCornerShape(percent = 22) does the same thing directly, no manual geometry needed.
//
// Defaults to Res.drawable.small -- that's the size the shipped artwork was actually meant for
// at HostEntryView's 64dp usage; Res.drawable.large is there for a bigger context (an About
// panel, not built yet) and callers should pass it explicitly when they need it.
@Composable
fun AppIconImage(
    modifier: Modifier = Modifier,
    resource: DrawableResource = Res.drawable.small,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = "Huck",
        modifier = modifier.clip(RoundedCornerShape(percent = 22)),
    )
}
