package com.quern.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.domain.model.CardSizePreset
import com.quern.app.ui.components.LocalQuernLayout
import com.quern.app.ui.components.QuernHomeSpacing
import com.quern.app.ui.components.QuernLayoutClass
import com.quern.app.R
import com.quern.app.data.servers.WebDavEntry
import com.quern.app.domain.model.DebridCloudItem
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaServerType
import com.quern.app.ui.components.LocalTvRailInset
import com.quern.app.ui.components.LumaBadgeChip
import com.quern.app.ui.components.LumaPosterCard
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

@Composable
fun LibraryScreen(
    favorites: List<MediaItem>,
    cloudItems: List<DebridCloudItem>,
    isLoadingCloud: Boolean,
    servers: List<MediaServerConfig>,
    webDavEntries: List<WebDavEntry>,
    isTv: Boolean,
    cardSize: CardSizePreset = CardSizePreset.STANDARD,
    onItemClick: (MediaItem) -> Unit,
    onRefreshCloud: () -> Unit,
    onPlayCloudItem: (DebridCloudItem) -> Unit,
    onBrowseWebDav: (String, String?) -> Unit,
    onPlayWebDavEntry: (MediaServerConfig, WebDavEntry) -> Unit
) {
    val palette = LocalQuernPalette.current
    val layout = LocalQuernLayout.current
    val isCompact = layout == QuernLayoutClass.COMPACT
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val railInset = if (isTv) LocalTvRailInset.current else 0.dp
    val navBarBottom = if (isTv) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = if (isTv) 44.dp else navBarBottom + 104.dp
    var selectedTab by remember { mutableIntStateOf(0) }
    val webDavServers = remember(servers) { servers.filter { it.type == MediaServerType.WEBDAV } }
    var activeWebDavServer by remember(webDavServers) { mutableStateOf(webDavServers.firstOrNull()) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) onRefreshCloud()
        val srv = activeWebDavServer
        if (selectedTab == 2 && srv != null) {
            onBrowseWebDav(srv.id, null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = if (isTv) railInset + 22.dp else 14.dp,
                end = if (isTv) 28.dp else 14.dp,
                top = if (isCompact) 14.dp else 18.dp,
                bottom = if (isTv) 18.dp else 0.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = qString(R.string.library_title),
                color = LumaTextPrimary,
                fontSize = if (isTv) 26.sp else 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (selectedTab == 1) {
                IconButton(onClick = onRefreshCloud) {
                    Icon(Icons.Default.Refresh, contentDescription = qString(R.string.library_refresh_cloud), tint = palette.focusRing)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(modifier = if (LocalQuernLayout.current == QuernLayoutClass.COMPACT) Modifier.horizontalScroll(rememberScrollState()) else Modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                qString(R.string.library_tab_favorites, favorites.size),
                qString(R.string.library_tab_debrid),
                qString(R.string.library_tab_webdav)
            ).forEachIndexed { idx, label ->
                var focused by remember { mutableStateOf(false) }
                val selected = selectedTab == idx
                Surface(
                    color = when {
                        focused -> palette.accent
                        selected -> Color.White
                        else -> palette.glassSurface
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        if (focused) 2.5.dp else 1.dp,
                        if (focused) palette.focusRing else if (selected) Color.White else palette.border
                    ),
                    modifier = Modifier
                        .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
                        .clip(RoundedCornerShape(999.dp))
                        .tvClickable(
                            onFocusChangedState = { focused = it },
                            onClick = { selectedTab = idx }
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = if (isCompact) 12.dp else 9.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (focused) palette.onAccent else if (selected) Color.Black else LumaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                if (favorites.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(qString(R.string.library_empty_favorites), color = LumaTextSecondary)
                    }
                } else {
                    val gridMinSize = if (isTv) {
                        maxOf(150.dp, (cardSize.tvWidthDp + 24).dp)
                    } else {
                        (QuernHomeSpacing.posterWidth(cardSize, layout, screenWidthDp) - 6).coerceAtLeast(88).dp
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(bottom = listBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(QuernHomeSpacing.cardHorizontalGap(layout)),
                        verticalArrangement = Arrangement.spacedBy(if (isCompact) 14.dp else 18.dp)
                    ) {
                        items(favorites, key = { "${it.origin}_${it.type}_${it.id}" }) { item ->
                            LumaPosterCard(
                                item = item,
                                isTv = isTv,
                                cardSize = cardSize,
                                fillGridWidth = !isTv,
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }

            1 -> {
                if (isLoadingCloud) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = palette.focusRing)
                    }
                } else if (cloudItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            qString(R.string.library_empty_cloud),
                            color = LumaTextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = listBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(cloudItems, key = { "${it.service}_${it.id}" }) { cloudItem ->
                            var focused by remember { mutableStateOf(false) }
                            Surface(
                                color = if (focused) palette.glassElevated else palette.glassSurface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(if (focused) 2.5.dp else 1.dp, if (focused) palette.focusRing else palette.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .tvClickable(
                                        onFocusChangedState = { focused = it },
                                        onClick = { onPlayCloudItem(cloudItem) }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CloudDownload, null, tint = palette.focusRing, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            cloudItem.name,
                                            color = LumaTextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LumaBadgeChip(cloudItem.service.displayName, palette.accent)
                                            LumaBadgeChip(cloudItem.status, LumaGreen)
                                        }
                                    }
                                    Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (webDavServers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(qString(R.string.library_empty_webdav), color = LumaTextSecondary)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            webDavServers.forEach { srv ->
                                var focused by remember { mutableStateOf(false) }
                                val selected = srv.id == activeWebDavServer?.id
                                Surface(
                                    color = if (focused) palette.accent else if (selected) Color.White else palette.glassSurface,
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) palette.focusRing else palette.border),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .tvClickable(
                                            onFocusChangedState = { focused = it },
                                            onClick = {
                                                activeWebDavServer = srv
                                                onBrowseWebDav(srv.id, null)
                                            }
                                        )
                                ) {
                                    Text(
                                        srv.name,
                                        color = if (focused) palette.onAccent else if (selected) Color.Black else LumaTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(webDavEntries, key = { it.fullUrl }) { entry ->
                                val srv = activeWebDavServer ?: return@items
                                var focused by remember { mutableStateOf(false) }
                                Surface(
                                    color = if (focused) palette.glassElevated else palette.glassSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(if (focused) 2.5.dp else 1.dp, if (focused) palette.focusRing else palette.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .tvClickable(
                                            onFocusChangedState = { focused = it },
                                            onClick = {
                                                if (entry.isDirectory) {
                                                    onBrowseWebDav(srv.id, entry.fullUrl)
                                                } else {
                                                    onPlayWebDavEntry(srv, entry)
                                                }
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (entry.isDirectory) palette.focusRing else palette.accent
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            entry.name,
                                            color = LumaTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
