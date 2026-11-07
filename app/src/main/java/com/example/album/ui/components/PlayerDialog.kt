package com.example.album.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Player dialogs use their own compact action row. Material AlertDialog keeps a
 * large reserved footer which consumed too much of the already short player
 * area, especially in landscape.
 */
@Composable
internal fun CompactPlayerDialog(
    title: String,
    closeLabel: String,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = .20f),
            contentColor = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column {
                Text(
                    title,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
                content()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text(closeLabel, color = Color.White)
                    }
                }
            }
        }
    }
}
