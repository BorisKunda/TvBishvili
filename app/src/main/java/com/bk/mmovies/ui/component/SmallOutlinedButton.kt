package com.bk.mmovies.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bk.mmovies.R

@Composable
fun SmallOutlinedButton(
        label: String,
        icon: ImageVector,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        isEnabled: Boolean = true,
) {
    val contentColor = Color(0xFFF5F5F5)
    val containerColor = Color.Transparent
    val borderColor = Color(0xFFE0E0E0)

    OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 36.dp),
            enabled = isEnabled,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                    width = 1.dp,
                    color = if (isEnabled) {
                        borderColor
                    } else {
                        borderColor.copy(alpha = 0.12f)
                    },
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                    disabledContainerColor = containerColor,
                    disabledContentColor = contentColor.copy(alpha = 0.38f),
            ),
            contentPadding = PaddingValues(
                    horizontal = 14.dp,
                    vertical = 6.dp,
            ),
    ) {
        Text(
                text = label,
                style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                ),
        )

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Previews
 */

//@Preview(showBackground = true)
//@Composable
//private fun SmallOutlinedButtonSharePreview() {
//    Box(
//            modifier = Modifier
//                    .fillMaxSize()
//                    .background(Color.Black),
//            contentAlignment = Alignment.Center,
//            propagateMinConstraints = false,
//            content = {
//                SmallOutlinedButton(
//                        label = stringResource(R.string.news_share_action),
//                        icon = Icons.Filled.Share,
//                        onClick = {},
//                        modifier = Modifier.wrapContentSize(),
//                        isEnabled = true
//                )
//            }
//    )
//}

@Preview(showBackground = true)
@Composable
private fun SmallOutlinedButtonReadArticlePreview() {
    Box(
            modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            contentAlignment = Alignment.Center,
            propagateMinConstraints = false,
            content = {
                SmallOutlinedButton(
                        stringResource(R.string.news_read_full_article),
                        Icons.AutoMirrored.Filled.OpenInNew,
                        {},
                        Modifier.wrapContentSize(),
                        true
                )
            })
}

