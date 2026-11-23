package com.quern.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.data.servers.ServerTestResult
import com.quern.app.domain.model.DebridAccountStatus
import com.quern.app.domain.model.DebridServiceType
import com.quern.app.domain.model.MediaServerConfig
import com.quern.app.domain.model.MediaServerType
import com.quern.app.domain.model.TmdbConfig
import com.quern.app.domain.model.TraktAuthState
import com.quern.app.domain.model.TraktDeviceCode
import com.quern.app.domain.model.UiModeOverride
import com.quern.app.ui.components.LumaBadgeChip
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaAccent
import com.quern.app.ui.theme.LumaBackground
import com.quern.app.ui.theme.LumaBorder
import com.quern.app.ui.theme.LumaCyan
import com.quern.app.ui.theme.LumaGlassBorder
import com.quern.app.ui.theme.LumaGlassFill
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaSurface
import com.quern.app.ui.theme.LumaSurfaceElevated
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.QuernGlassBorderBrush
import com.quern.app.ui.theme.QuernPrimaryGradient
import com.quern.app.ui.theme.qString

@Composable
fun ServersScreen(
    debridStatuses: Map<DebridServiceType, DebridAccountStatus>,
    getDebridKey: (DebridServiceType) -> String,
    mediaServers: List<MediaServerConfig>,
    tmdbConfig: TmdbConfig,
    traktState: TraktAuthState,
    traktDeviceCode: TraktDeviceCode?,
    uiModeOverride: UiModeOverride,
    isTv: Boolean,
    onSaveDebridKey: (DebridServiceType, String) -> Unit,
    onSaveMediaServer: (MediaServerConfig, (ServerTestResult) -> Unit) -> Unit,
    onDeleteMediaServer: (String) -> Unit,
    onSaveTmdb: (String, String) -> Unit,
    onStartTraktAuth: (String, String) -> Unit,
    onConfirmTraktAuth: (String, String) -> Unit,
    onDisconnectTrakt: () -> Unit,
    onUiModeChange: (UiModeOverride) -> Unit,
    initialSection: Int = 0
) {
    var selectedSection by remember(initialSection) { mutableIntStateOf(initialSection) }
    var showAddServerDialog by remember { mutableStateOf(false) }
    var dialogInitialServerType by remember { mutableStateOf(MediaServerType.JELLYFIN) }

    val connectedDebridCount = debridStatuses.values.count { it.isConnected }
    val syncCount = (if (tmdbConfig.isConfigured) 1 else 0) + (if (traktState.isAuthenticated) 1 else 0)

    val palette = LocalQuernPalette.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentPadding = PaddingValues(
            start = if (isTv) 32.dp else 18.dp,
            end = if (isTv) 32.dp else 18.dp,
            top = 20.dp,
            bottom = 112.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Editorial Header
        item(key = "title") {
            Column {
                Text(
                    text = qString(R.string.servers_hub_eyebrow),
                    color = LumaCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = qString(R.string.servers_hub_title),
                    color = LumaTextPrimary,
                    fontSize = if (isTv) 28.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(14.dp))

                // Glowing Overview Stats Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ConnectionStatTile(
                        title = qString(R.string.servers_stat_debrid),
                        value = qString(R.string.servers_stat_active, connectedDebridCount),
                        icon = Icons.Default.Bolt,
                        accent = LumaGreen,
                        modifier = Modifier.weight(1f)
                    )
                    ConnectionStatTile(
                        title = qString(R.string.servers_stat_servers),
                        value = qString(R.string.servers_stat_linked, mediaServers.size),
                        icon = Icons.Default.Dns,
                        accent = LumaCyan,
                        modifier = Modifier.weight(1f)
                    )
                    ConnectionStatTile(
                        title = qString(R.string.servers_stat_sync),
                        value = "$syncCount / 2",
                        icon = Icons.Default.Sync,
                        accent = LumaAccent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Sleek Segmented Pill Navigation
        item(key = "segments") {
            Surface(
                color = LumaSurface,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, LumaGlassBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        qString(R.string.servers_tab_debrid),
                        qString(R.string.servers_tab_media),
                        qString(R.string.servers_tab_sync)
                    ).forEachIndexed { idx, label ->
                        val active = selectedSection == idx
                        var focused by remember { mutableStateOf(false) }
                        val palette = LocalQuernPalette.current
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    when {
                                        focused -> palette.accent
                                        active -> Color.White
                                        else -> Color.Transparent
                                    }
                                )
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = { selectedSection = idx }
                                )
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (focused) palette.onAccent else if (active) Color.Black else LumaTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        when (selectedSection) {
            0 -> {
                items(DebridServiceType.entries, key = { it.name }) { type ->
                    DebridServiceCard(
                        type = type,
                        status = debridStatuses[type],
                        initialKey = remember(type) { getDebridKey(type) },
                        onSave = { key -> onSaveDebridKey(type, key) }
                    )
                }
            }

            1 -> {
                item(key = "branded_media_servers") {
                    val providers = listOf(
                        Triple(MediaServerType.JELLYFIN, "Jellyfin", qString(R.string.servers_desc_jellyfin)),
                        Triple(MediaServerType.EMBY, "Emby", qString(R.string.servers_desc_emby)),
                        Triple(MediaServerType.PLEX, "Plex", qString(R.string.servers_desc_plex)),
                        Triple(MediaServerType.WEBDAV, "WebDAV / Custom", qString(R.string.servers_desc_webdav))
                    )
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            providers.forEachIndexed { idx, (type, title, desc) ->
                                var focused by remember { mutableStateOf(false) }
                                val brandColor = when (type) {
                                    MediaServerType.JELLYFIN -> Color(0xFF9B6DFF)
                                    MediaServerType.EMBY -> Color(0xFF52B54B)
                                    MediaServerType.PLEX -> Color(0xFFE5A00D)
                                    MediaServerType.WEBDAV -> Color(0xFF38BDF8)
                                }
                                val brandBadge = when (type) {
                                    MediaServerType.JELLYFIN -> "▲"
                                    MediaServerType.EMBY -> "▶"
                                    MediaServerType.PLEX -> "❯"
                                    MediaServerType.WEBDAV -> "≡"
                                }
                                val linkedCount = mediaServers.count { it.type == type }
                                val rowBg by animateColorAsState(
                                    targetValue = if (focused) palette.accent else Color.Transparent,
                                    animationSpec = tween(130),
                                    label = "media_provider_bg"
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(rowBg)
                                        .border(
                                            width = if (focused) 2.5.dp else 0.dp,
                                            color = if (focused) palette.focusRing else Color.Transparent,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .tvClickable(
                                            onFocusChangedState = { focused = it },
                                            onClick = {
                                                dialogInitialServerType = type
                                                showAddServerDialog = true
                                            }
                                        )
                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(
                                                if (focused) Color.Black.copy(alpha = 0.22f)
                                                else brandColor.copy(alpha = 0.18f)
                                            )
                                            .border(1.dp, brandColor.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = brandBadge,
                                            color = if (focused) palette.onAccent else brandColor,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = title,
                                                color = if (focused) palette.onAccent else LumaTextPrimary,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (linkedCount > 0) {
                                                LumaBadgeChip(qString(R.string.servers_connected_count, linkedCount), LumaGreen)
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            color = if (focused) palette.onAccent.copy(alpha = 0.85f) else LumaTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Connect $title",
                                        tint = if (focused) palette.onAccent else LumaTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (idx < providers.lastIndex) {
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }

                if (mediaServers.isNotEmpty()) {
                    item(key = "linked_servers_header") {
                        Text(
                            text = qString(R.string.servers_connected_header),
                            color = LumaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(mediaServers, key = { it.id }) { server ->
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(LumaCyan.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Dns, null, tint = LumaCyan)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(server.name, color = LumaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        LumaBadgeChip(server.type.displayName, palette.accent)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(server.baseUrl, color = LumaTextSecondary, fontSize = 12.sp)
                                }
                                IconButton(onClick = { onDeleteMediaServer(server.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LumaRed)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                item(key = "tmdb_card") {
                    TmdbConfigCard(config = tmdbConfig, onSave = onSaveTmdb)
                }
                item(key = "trakt_card") {
                    TraktConfigCard(
                        state = traktState,
                        deviceCode = traktDeviceCode,
                        onStartAuth = onStartTraktAuth,
                        onConfirmAuth = onConfirmTraktAuth,
                        onDisconnect = onDisconnectTrakt
                    )
                }
                item(key = "display_mode") {
                    Surface(
                        color = LumaSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, QuernGlassBorderBrush),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(qString(R.string.servers_display_mode_title), color = LumaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                qString(R.string.servers_display_mode_desc),
                                color = LumaTextSecondary,
                                fontSize = 12.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UiModeOverride.entries.forEach { mode ->
                                    val selected = mode == uiModeOverride
                                    Surface(
                                        color = if (selected) Color.White else LumaGlassFill,
                                        shape = RoundedCornerShape(999.dp),
                                        border = if (selected) null else BorderStroke(1.dp, LumaGlassBorder),
                                        modifier = Modifier.clickable { onUiModeChange(mode) }
                                    ) {
                                        Text(
                                            text = mode.label.substringBefore(" "),
                                            color = if (selected) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
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

    if (showAddServerDialog) {
        AddMediaServerDialog(
            initialType = dialogInitialServerType,
            onDismiss = { showAddServerDialog = false },
            onSave = { config ->
                onSaveMediaServer(config) { result ->
                    if (result.success) showAddServerDialog = false
                }
            }
        )
    }
}

@Composable
private fun ConnectionStatTile(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = LumaSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                Text(title, color = LumaTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(value, color = LumaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun DebridServiceCard(
    type: DebridServiceType,
    status: DebridAccountStatus?,
    initialKey: String,
    onSave: (String) -> Unit
) {
    var apiKey by remember(initialKey) { mutableStateOf(initialKey) }
    var editing by remember { mutableStateOf(false) }
    val connected = status?.isConnected == true

    Surface(
        color = LumaSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = if (connected) {
                Brush.linearGradient(listOf(LumaGreen.copy(alpha = 0.65f), LumaCyan.copy(alpha = 0.2f)))
            } else {
                QuernGlassBorderBrush
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (connected) LumaGreen.copy(alpha = 0.18f) else LumaAccent.copy(alpha = 0.16f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Default.CheckCircle else Icons.Default.Key,
                        contentDescription = null,
                        tint = if (connected) LumaGreen else LumaAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(type.displayName, color = LumaTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        if (connected) {
                            LumaBadgeChip(status?.plan ?: "Active", LumaGreen)
                        }
                    }
                    Text(
                        text = if (connected && !status?.username.isNullOrBlank()) {
                            qString(R.string.servers_signed_in_as, status?.username.orEmpty())
                        } else {
                            type.tagline
                        },
                        color = if (connected) LumaCyan else LumaTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (!status?.error.isNullOrBlank()) {
                Text(status?.error.orEmpty(), color = LumaRed, fontSize = 12.sp)
            }

            if (editing) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("${type.displayName} API Key (${type.apiDocsHint})") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LumaCyan,
                        unfocusedBorderColor = LumaBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            onSave(apiKey)
                            editing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(qString(R.string.servers_verify_save), fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { editing = false }) {
                        Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                    }
                }
            } else {
                val pal = LocalQuernPalette.current
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    var btnFocused by remember { mutableStateOf(false) }
                    Surface(
                        color = when {
                            btnFocused -> pal.accent
                            connected -> LumaGlassFill
                            else -> Color.White
                        },
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(
                            if (btnFocused) 2.5.dp else 1.dp,
                            if (btnFocused) pal.focusRing else if (connected) LumaGlassBorder else Color.White
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .tvClickable(
                                onFocusChangedState = { btnFocused = it },
                                onClick = { editing = true }
                            )
                    ) {
                        Text(
                            text = if (connected) qString(R.string.servers_update_key) else qString(R.string.servers_connect_name, type.displayName),
                            color = if (btnFocused) pal.onAccent else if (connected) Color.White else Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                    if (connected) {
                        var discFocused by remember { mutableStateOf(false) }
                        Surface(
                            color = if (discFocused) LumaRed else LumaRed.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(if (discFocused) 2.dp else 1.dp, LumaRed),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .tvClickable(
                                    onFocusChangedState = { discFocused = it },
                                    onClick = {
                                        apiKey = ""
                                        onSave("")
                                    }
                                )
                        ) {
                            Text(
                                text = qString(R.string.servers_disconnect),
                                color = if (discFocused) Color.White else LumaRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TmdbConfigCard(
    config: TmdbConfig,
    onSave: (String, String) -> Unit
) {
    var apiKey by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var language by remember(config.language) { mutableStateOf(config.language) }
    var editing by remember { mutableStateOf(false) }

    Surface(
        color = LumaSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (config.isConfigured) LumaGreen.copy(alpha = 0.55f) else LumaGlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LumaCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MovieFilter, null, tint = LumaCyan)
                    }
                    Column {
                        Text(qString(R.string.servers_tmdb_title), color = LumaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(qString(R.string.servers_tmdb_desc), color = LumaTextSecondary, fontSize = 12.sp)
                    }
                }
                if (config.isConfigured) LumaBadgeChip(qString(R.string.servers_tmdb_connected), LumaGreen)
            }

            if (editing) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(qString(R.string.servers_tmdb_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("en-US" to "English", "ar-SA" to "العربية").forEach { (code, label) ->
                        FilterChip(selected = language == code, onClick = { language = code }, label = { Text(label) })
                    }
                }
                Button(
                    onClick = {
                        onSave(apiKey, language)
                        editing = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(qString(R.string.servers_tmdb_save), fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    color = if (config.isConfigured) LumaGlassFill else Color.White,
                    shape = RoundedCornerShape(999.dp),
                    border = if (config.isConfigured) BorderStroke(1.dp, LumaGlassBorder) else null,
                    modifier = Modifier.clickable { editing = true }
                ) {
                    Text(
                        text = if (config.isConfigured) qString(R.string.servers_tmdb_configure, config.language) else qString(R.string.servers_tmdb_connect),
                        color = if (config.isConfigured) Color.White else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TraktConfigCard(
    state: TraktAuthState,
    deviceCode: TraktDeviceCode?,
    onStartAuth: (String, String) -> Unit,
    onConfirmAuth: (String, String) -> Unit,
    onDisconnect: () -> Unit
) {
    var clientId by remember(state.clientId) { mutableStateOf(state.clientId) }
    var clientSecret by remember(state.clientSecret) { mutableStateOf(state.clientSecret) }
    var editing by remember { mutableStateOf(false) }

    Surface(
        color = LumaSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (state.isAuthenticated) LumaGreen.copy(alpha = 0.55f) else LumaGlassBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(qString(R.string.servers_trakt_title), color = LumaTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(qString(R.string.servers_trakt_desc), color = LumaTextSecondary, fontSize = 12.sp)
                }
                if (state.isAuthenticated) LumaBadgeChip(state.username ?: qString(R.string.servers_tmdb_connected), LumaGreen)
            }

            if (deviceCode != null) {
                Surface(color = LumaSurfaceElevated, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(qString(R.string.servers_trakt_visit, deviceCode.verificationUrl), color = LumaTextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(deviceCode.userCode, color = LumaCyan, fontSize = 28.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onConfirmAuth(clientId, clientSecret) },
                            colors = ButtonDefaults.buttonColors(containerColor = LumaGreen),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(qString(R.string.servers_trakt_complete))
                        }
                    }
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text(qString(R.string.servers_trakt_client_id)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text(qString(R.string.servers_trakt_client_secret)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onStartAuth(clientId, clientSecret) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(qString(R.string.servers_trakt_generate), fontWeight = FontWeight.Bold)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = if (state.isAuthenticated) LumaGlassFill else Color.White,
                        shape = RoundedCornerShape(999.dp),
                        border = if (state.isAuthenticated) BorderStroke(1.dp, LumaGlassBorder) else null,
                        modifier = Modifier.clickable { editing = true }
                    ) {
                        Text(
                            text = if (state.isAuthenticated) qString(R.string.servers_trakt_manage) else qString(R.string.servers_trakt_connect),
                            color = if (state.isAuthenticated) Color.White else Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                    if (state.isAuthenticated) {
                        OutlinedButton(onClick = onDisconnect, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, LumaRed.copy(alpha = 0.5f))) {
                            Text(qString(R.string.servers_disconnect), color = LumaRed, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMediaServerDialog(
    initialType: MediaServerType = MediaServerType.JELLYFIN,
    onDismiss: () -> Unit,
    onSave: (MediaServerConfig) -> Unit
) {
    var type by remember(initialType) { mutableStateOf(initialType) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LumaSurfaceElevated,
        title = { Text(qString(R.string.servers_add_dialog_title), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaServerType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.displayName.substringBefore(" "), fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(qString(R.string.servers_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(qString(R.string.servers_field_url, type.defaultPortHint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = {
                        Text(
                            when (type) {
                                MediaServerType.PLEX -> qString(R.string.servers_field_plex_token)
                                MediaServerType.WEBDAV -> qString(R.string.servers_field_bearer_token)
                                else -> qString(R.string.servers_field_api_key)
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (type != MediaServerType.PLEX) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(qString(R.string.servers_field_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(qString(R.string.servers_field_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        MediaServerConfig(
                            id = "",
                            name = name.ifBlank { type.displayName },
                            type = type,
                            baseUrl = baseUrl.trim(),
                            apiKeyOrToken = token.trim(),
                            username = username.trim(),
                            password = password
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(qString(R.string.servers_test_connect), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(qString(R.string.common_cancel)) }
        }
    )
}
