package com.quern.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.data.servers.WebDavEntry
import com.quern.app.domain.model.DebridCloudItem
import com.quern.app.domain.model.MediaItem
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaServerType
import com.quern.app.ui.components.LumaBadgeChip
import com.quern.app.ui.components.LumaPosterCard
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary

@Composable
fun LibraryScreen(
    favorites: List<MediaItem>,
    cloudItems: List<DebridCloudItem>,
    isLoadingCloud: Boolean,
    servers: List<MediaServerConfig>,
    webDavEntries: List<WebDavEntry>,
    isTv: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onRefreshCloud: () -> Unit,
    onPlayCloudItem: (DebridCloudItem) -> Unit,
    onBrowseWebDav: (String, String?) -> Unit,
    onPlayWebDavEntry: (MediaServerConfig, WebDavEntry) -> Unit
) {
    val palette = LocalQuernPalette.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val webDavServers = remember(servers) { servers.filter { it.type == MediaServerType.WEBDAV } }
    var activeWebDavServer by remember(webDavServers) { mutableStateOf(webDavServers.firstOrNull()) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) onRefreshCloud()
        if (selectedTab == 2 && activeWebDavServer != null) {
            onBrowseWebDav(activeWebDavServer!!.id, null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = if (isTv) 28.dp else 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Library & Cloud",
                color = LumaTextPrimary,
                fontSize = if (isTv) 26.sp else 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (selectedTab == 1) {
                IconButton(onClick = onRefreshCloud) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Cloud", tint = palette.focusRing)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Favorites (${favorites.size})", "Debrid Cloud", "WebDAV Files").forEachIndexed { idx, label ->
                var focused by remember { mutableStateOf(false) }
                val selected = selectedTab == idx
                Surface(
                    color = when {
                        focused -> palette.accent
                        selected -> Color.White
                        else -> palette.surface
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        if (focused) 2.5.dp else 1.dp,
                        if (focused) palette.focusRing else if (selected) Color.White else palette.border
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .tvClickable(
                            onFocusChangedState = { focused = it },
                            onClick = { selectedTab = idx }
                        )
                ) {
                    Text(
                        text = label,
                        color = if (focused) palette.onAccent else if (selected) Color.Black else LumaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                if (favorites.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No favorites saved yet.", color = LumaTextSecondary)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isTv) 150.dp else 118.dp),
                        contentPadding = PaddingValues(bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(favorites, key = { it.id }) { item ->
                            LumaPosterCard(item = item, isTv = isTv, onClick = { onItemClick(item) })
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
                            "No cloud files found. Connect TorBox, Real-Debrid, AllDebrid, or Premiumize in Settings → Services.",
                            color = LumaTextSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(cloudItems, key = { "${it.service}_${it.id}" }) { cloudItem ->
                            var focused by remember { mutableStateOf(false) }
                            Surface(
                                color = if (focused) palette.surfaceElevated else palette.surface,
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
                        Text("No WebDAV servers configured. Add one in Settings → Services.", color = LumaTextSecondary)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            webDavServers.forEach { srv ->
                                var focused by remember { mutableStateOf(false) }
                                val selected = srv.id == activeWebDavServer?.id
                                Surface(
                                    color = if (focused) palette.accent else if (selected) Color.White else palette.surface,
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
                                    color = if (focused) palette.surfaceElevated else palette.surface,
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
