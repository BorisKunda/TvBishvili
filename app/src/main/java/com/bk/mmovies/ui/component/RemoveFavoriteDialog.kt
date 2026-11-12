package com.bk.mmovies.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bk.mmovies.R
import com.bk.mmovies.ui.theme.MMoviesTheme
import com.bk.mmovies.ui.theme.primaryButtonCornerSize
import com.bk.mmovies.ui.theme.primaryButtonHeight

private val dialogWidthFraction = 0.92f
private val dialogCornerShape = 20.dp
private val dialogContentPadding = 20.dp
private val messageToButtonsSpacing = 24.dp
private val buttonsSpacing = 12.dp
private val outlineButtonBorderStrokeWidth = 1.dp
private const val OUTLINE_BORDER_ALPHA = 0.4f

@Composable
fun RemoveFavoriteDialog(
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
                        ) {
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
          ) {
        RemoveFavoriteDialogContent(
                onConfirm = onConfirm,
                onDismiss = onDismiss
                                   )
    }
}

@Composable
private fun RemoveFavoriteDialogContent(
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
                                       ) {
    Surface(
            modifier = Modifier.fillMaxWidth(dialogWidthFraction),
            shape = RoundedCornerShape(dialogCornerShape),
            color = MaterialTheme.colorScheme.surface
           ) {
        Column(modifier = Modifier.padding(dialogContentPadding)) {
            Text(
                    text = stringResource(R.string.remove_favorite_dialog_message),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
            Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = messageToButtonsSpacing),
                    horizontalArrangement = Arrangement.spacedBy(buttonsSpacing)
               ) {
                OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                                .weight(1f)
                                .height(primaryButtonHeight),
                        shape = RoundedCornerShape(primaryButtonCornerSize),
                        border = BorderStroke(
                                width = outlineButtonBorderStrokeWidth,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = OUTLINE_BORDER_ALPHA)
                                             ),
                        colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                                                                    )
                              ) {
                    Text(
                            text = stringResource(R.string.remove_favorite_dialog_cancel),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                }
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(
                            imageResId = null,
                            label = stringResource(R.string.remove_favorite_dialog_confirm),
                            onClick = onConfirm,
                            isEnabled = true
                                 )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RemoveFavoriteDialogContentPreview() {
    MMoviesTheme {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                RemoveFavoriteDialogContent(onConfirm = {}, onDismiss = {})
            }
        }
    }
}
