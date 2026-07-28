package com.getmoney.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.getmoney.app.ui.theme.InkMuted
import com.getmoney.app.ui.theme.Surface1

object SlipImagePreviewDefaults {
    val ThumbSize: Dp = 120.dp
}

/**
 * Shared slip image UX: thumbnail → tap for full-screen viewer
 * (close button or tap backdrop).
 */
@Composable
fun SlipImagePreview(
    data: Any?,
    modifier: Modifier = Modifier,
    thumbSize: Dp = SlipImagePreviewDefaults.ThumbSize,
    showTapHint: Boolean = false,
    enableFullscreenOnTap: Boolean = true,
    contentDescription: String? = "Slip image",
) {
    var showFullImage by remember(data) { mutableStateOf(false) }
    val context = LocalContext.current
    val hasImage = data != null && !(data is String && data.isBlank())

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(thumbSize)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .background(Surface1)
                .then(
                    if (hasImage && enableFullscreenOnTap) {
                        Modifier.clickable { showFullImage = true }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (hasImage) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(data)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (hasImage && showTapHint) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "แตะรูปเพื่อดูขนาดเต็ม",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
            )
        }
    }

    if (showFullImage && hasImage) {
        Dialog(
            onDismissRequest = { showFullImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { showFullImage = false },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(data)
                        .crossfade(true)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 56.dp)
                        .align(Alignment.Center)
                        .clickable(enabled = false) {},
                )
                IconButton(
                    onClick = { showFullImage = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "ปิด",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
