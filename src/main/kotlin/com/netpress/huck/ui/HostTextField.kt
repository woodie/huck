package com.netpress.huck.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// A shared host-input field for HostEntryView and ScanGridView's toolbar. Material's
// OutlinedTextField enforces a hardcoded ~56dp minimum height (TextFieldDefaults.MinHeight) for
// its floating-label layout -- confirmed too tall on a real run, dwarfing the 64dp app icon next
// to it and not matching zouk's compact native macOS field. BasicTextField with a thin manual
// border sidesteps that entirely: no floating-label space reserved, height is just text line
// height + this composable's own padding, giving a field close to zouk's actual proportions.
//
// textAlign defaults to Start (a toolbar/URL-bar field reads left to right); HostEntryView
// passes Center to match zouk's centered host field on that screen, confirmed on a real run.
@Composable
fun HostTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    onSubmit: (() -> Unit)? = null,
) {
    var fieldModifier =
        modifier
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)

    if (onSubmit != null) {
        fieldModifier =
            fieldModifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                    onSubmit()
                    true
                } else {
                    false
                }
            }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle =
            MaterialTheme.typography.body1.copy(
                color = MaterialTheme.colors.onSurface,
                textAlign = textAlign,
            ),
        cursorBrush = SolidColor(MaterialTheme.colors.onSurface),
        modifier = fieldModifier,
    )
}
