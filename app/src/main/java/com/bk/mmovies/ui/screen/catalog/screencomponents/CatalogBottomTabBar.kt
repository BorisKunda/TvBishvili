package com.bk.mmovies.ui.screen.catalog.screencomponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bk.mmovies.core.R as CoreR

// NavigationBar enforces an 80dp minimum height internally, so shrinking our
// own padding just left Compose's auto-centering fill the rest of that fixed
// height — the visible gap barely moved. Surface has no such minimum, so the
// bar's height (and therefore the icon+label block's visible margin) is
// driven entirely by this padding.
private val tabHeight = 56.dp
private val tabDividerWidth = 1.dp
private val tabDividerVerticalPadding = 10.dp
private const val TAB_DIVIDER_ALPHA = 0.18f

private const val MAX_TAB_BAR_FONT_SCALE = 1.3f

@Composable
fun CatalogBottomTabBar(
        selectedTab: CatalogBottomTab,
        onTabSelected: (CatalogBottomTab) -> Unit
                    ) {
    val density = LocalDensity.current
    val cappedDensity = Density(
            density = density.density,
            fontScale = density.fontScale.coerceAtMost(MAX_TAB_BAR_FONT_SCALE)
                               )
    CompositionLocalProvider(LocalDensity provides cappedDensity) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = NavigationBarDefaults.Elevation
           ) {
        // Arrangement.SpaceEvenly spreads today's few tabs across the full
        // width like a regular bottom bar, instead of clustering them into a
        // single tight block in the center; once enough tabs are added to
        // overflow the width, the list simply scrolls instead of shrinking
        // every tab to fit.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tabCount = CatalogBottomTab.entries.size
        val tabWidth = (maxWidth - tabDividerWidth * (tabCount - 1)) / tabCount
        LazyRow(
                modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly
               ) {
            itemsIndexed(
                    CatalogBottomTab.entries,
                    key = { _, tab -> tab.name }
                        ) { index, tab ->
                // VerticalDivider defaults to fillMaxHeight(); without a
                // bounded height on this Row it would stretch to consume the
                // rest of the screen.
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    if (index != 0) {
                        VerticalDivider(
                                modifier = Modifier.padding(vertical = tabDividerVerticalPadding),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = TAB_DIVIDER_ALPHA)
                                        )
                    }
                    val isSelected = selectedTab == tab
                    val contentColor: Color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(
                            modifier = Modifier
                                    .width(tabWidth)
                                    .height(tabHeight)
                                    .selectable(
                                            selected = isSelected,
                                            role = Role.Tab,
                                            onClick = { onTabSelected(tab) }
                                               ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                          ) {
                        Icon(
                                painter = tab.iconPainter(),
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(tab.iconSize)
                            )
                    }
                }
            }
        }
        }
    }
    }
}

enum class CatalogBottomTab(
        val iconPainter: @Composable () -> Painter,
        val iconSize: Dp = 24.dp
                           ) {
    Movies({ rememberVectorPainter(Icons.Filled.Movie) }),
    TvSeries({ rememberVectorPainter(Icons.Filled.Tv) }),
    News({ rememberVectorPainter(Icons.Filled.Newspaper) }),
    AiRecommendations({ painterResource(CoreR.drawable.ic_ai) }, iconSize = 32.dp)
}
