package com.getmoney.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.Hairline
import com.getmoney.app.ui.theme.InkMuted
import com.getmoney.app.ui.theme.Surface1

/**
 * Shared Carbon-style dialog shell used across GetMoney popups.
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    supportingText: String? = null,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    primaryEnabled: Boolean = true,
    primaryDestructive: Boolean = false,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
    tertiaryDestructive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, Hairline)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Surface1),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!supportingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                )
            }
            if (content != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            }
            if (primaryLabel != null || secondaryLabel != null || tertiaryLabel != null) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Hairline)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tertiaryLabel != null && onTertiary != null) {
                        TextButton(onClick = onTertiary) {
                            Text(
                                text = tertiaryLabel,
                                color = if (tertiaryDestructive) ErrorRed else InkMuted,
                            )
                        }
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        TextButton(onClick = onSecondary) {
                            Text(secondaryLabel, color = InkMuted)
                        }
                    }
                    if (primaryLabel != null && onPrimary != null) {
                        Button(
                            onClick = onPrimary,
                            enabled = primaryEnabled,
                            shape = MaterialTheme.shapes.small,
                            colors = if (primaryDestructive) {
                                ButtonDefaults.buttonColors(
                                    containerColor = ErrorRed,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                CarbonButtonDefaults.primaryButtonColors()
                            },
                            elevation = CarbonButtonDefaults.primaryButtonElevation(),
                        ) {
                            Text(primaryLabel)
                        }
                    }
                }
            }
        }
    }
}
