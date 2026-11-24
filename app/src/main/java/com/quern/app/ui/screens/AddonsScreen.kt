package com.quern.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.quern.app.ui.components.QuernAlertDialog as AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.quern.app.R
import com.quern.app.data.addons.AddonHealthResult
import com.quern.app.data.companion.CompanionSessionState
import com.quern.app.domain.model.InstalledAddon
import com.quern.app.ui.components.LumaBadgeChip
import com.quern.app.ui.components.QuernCompanionSignInCard
import com.quern.app.ui.components.isTvHardware
import com.quern.app.ui.components.quernSwitchColors
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.frostedSheen
import com.quern.app.ui.theme.glossySheen
import com.quern.app.ui.theme.qString

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddonsScreen(
    addons: List<InstalledAddon>,
    addonsQrUrl: String,
    isTv: Boolean,
    companionSession: CompanionSessionState = CompanionSessionState("QRN-000", System.currentTimeMillis() + 600_000L),
    onRefreshCompanionSession: () -> Unit = {},
    onBindCompanionServer: () -> Unit = {},
    onUnbindCompanionServer: () -> Unit = {},
    hiddenCatalogKeys: Set<String> = emptySet(),
    healthMap: Map<String, AddonHealthResult> = emptyMap(),
    checkingUrls: Set<String> = emptySet(),
    isRefreshingAddons: Boolean = false,
    onInstallAddon: (String, () -> Unit) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit = {},
    onRemove: (String) -> Unit,
    onCheckHealth: (String) -> Unit = {},
    onCheckAllHealth: () -> Unit = {},
    onRefreshAddons: () -> Unit = {},
    onToggleCatalogVisibility: (String) -> Unit = {}
) {
    val palette = LocalQuernPalette.current
    val isTvDevice = isTvHardware(LocalContext.current)
    var manifestUrlInput by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    if (isTv) {
        // Spacious full-width TV workspace (no cramped 3rd column — QR setup opens cleanly in a modal!)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AddonUrlInstallerCard(
                manifestUrlInput = manifestUrlInput,
                isTv = true,
                hasAddons = addons.isNotEmpty(),
                isCheckingAny = checkingUrls.isNotEmpty(),
                isRefreshingAddons = isRefreshingAddons,
                onUrlChange = { manifestUrlInput = it },
                isInstalling = isInstalling,
                onInstallUrl = { url ->
                    if (url.isNotBlank() && !isInstalling) {
                        isInstalling = true
                        onInstallAddon(url) {
                            isInstalling = false
                            if (url == manifestUrlInput) manifestUrlInput = ""
                        }
                    }
                },
                onCheckAllHealth = onCheckAllHealth,
                onRefreshAddons = onRefreshAddons,
                onOpenQrSetup = if (isTvDevice) { { showQrDialog = true } } else null
            )

            Text(
                text = qString(R.string.addons_installed_count, addons.size),
                color = LumaTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            if (addons.isEmpty()) {
                EmptyAddonsBanner()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(addons, key = { _, a -> a.manifestUrl }) { idx, addon ->
                        InstalledAddonCard(
                            addon = addon,
                            canMoveUp = idx > 0,
                            canMoveDown = idx < addons.lastIndex,
                            health = healthMap[addon.manifestUrl],
                            isCheckingHealth = addon.manifestUrl in checkingUrls,
                            hiddenCatalogKeys = hiddenCatalogKeys,
                            onToggleEnabled = { onToggleEnabled(addon.manifestUrl, !addon.enabled) },
                            onMoveUp = { onMoveUp(addon.manifestUrl) },
                            onMoveDown = { onMoveDown(addon.manifestUrl) },
                            onRemove = { onRemove(addon.manifestUrl) },
                            onCheckHealth = { onCheckHealth(addon.manifestUrl) },
                            onToggleCatalogVisibility = onToggleCatalogVisibility
                        )
                    }
                }
            }
        }

        if (showQrDialog) {
            AlertDialog(
                onDismissRequest = { showQrDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .widthIn(max = 680.dp),
                containerColor = palette.surfaceElevated,
                text = {
                    QuernCompanionSignInCard(
                        url = addonsQrUrl,
                        session = companionSession,
                        onRefreshSession = onRefreshCompanionSession,
                        onBindServer = onBindCompanionServer,
                        onUnbindServer = onUnbindCompanionServer,
                        compactVertical = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showQrDialog = false }) {
                        Text(qString(R.string.common_close), color = palette.focusRing, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AddonUrlInstallerCard(
                manifestUrlInput = manifestUrlInput,
                isTv = false,
                hasAddons = addons.isNotEmpty(),
                isCheckingAny = checkingUrls.isNotEmpty(),
                isRefreshingAddons = isRefreshingAddons,
                onUrlChange = { manifestUrlInput = it },
                isInstalling = isInstalling,
                onInstallUrl = { url ->
                    if (url.isNotBlank() && !isInstalling) {
                        isInstalling = true
                        onInstallAddon(url) {
                            isInstalling = false
                            if (url == manifestUrlInput) manifestUrlInput = ""
                        }
                    }
                },
                onCheckAllHealth = onCheckAllHealth,
                onRefreshAddons = onRefreshAddons
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isTvDevice) {
                    item(key = "qr_phone") {
                        QuernCompanionSignInCard(
                            url = addonsQrUrl,
                            session = companionSession,
                            onRefreshSession = onRefreshCompanionSession,
                            onBindServer = onBindCompanionServer,
                            onUnbindServer = onUnbindCompanionServer,
                            compactVertical = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item(key = "installed_count") {
                    Text(
                        text = qString(R.string.addons_installed_count, addons.size),
                        color = LumaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (addons.isEmpty()) {
                    item(key = "empty_addons") { EmptyAddonsBanner() }
                } else {
                    itemsIndexed(addons, key = { _, a -> a.manifestUrl }) { idx, addon ->
                        InstalledAddonCard(
                            addon = addon,
                            canMoveUp = idx > 0,
                            canMoveDown = idx < addons.lastIndex,
                            health = healthMap[addon.manifestUrl],
                            isCheckingHealth = addon.manifestUrl in checkingUrls,
                            hiddenCatalogKeys = hiddenCatalogKeys,
                            onToggleEnabled = { onToggleEnabled(addon.manifestUrl, !addon.enabled) },
                            onMoveUp = { onMoveUp(addon.manifestUrl) },
                            onMoveDown = { onMoveDown(addon.manifestUrl) },
                            onRemove = { onRemove(addon.manifestUrl) },
                            onCheckHealth = { onCheckHealth(addon.manifestUrl) },
                            onToggleCatalogVisibility = onToggleCatalogVisibility
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAddonsBanner() {
    val palette = LocalQuernPalette.current
    val isTvDevice = isTvHardware(LocalContext.current)
    Surface(
        color = palette.glassSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .frostedSheen(cornerRadius = 18.dp, intensity = if (palette.isGlossy) 0.95f else 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Extension, contentDescription = null, tint = palette.focusRing, modifier = Modifier.size(28.dp))
            Text(
                text = qString(if (isTvDevice) R.string.addons_empty_state else R.string.addons_empty_state_phone),
                color = LumaTextSecondary,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddonUrlInstallerCard(
    manifestUrlInput: String,
    isTv: Boolean,
    hasAddons: Boolean,
    isCheckingAny: Boolean,
    isRefreshingAddons: Boolean = false,
    onUrlChange: (String) -> Unit,
    isInstalling: Boolean,
    onInstallUrl: (String) -> Unit,
    onCheckAllHealth: () -> Unit,
    onRefreshAddons: () -> Unit = {},
    onOpenQrSetup: (() -> Unit)? = null
) {
    val palette = LocalQuernPalette.current
    var showUrlDialog by remember { mutableStateOf(false) }

    Surface(
        color = palette.glassSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .frostedSheen(cornerRadius = 18.dp, intensity = if (palette.isGlossy) 0.95f else 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isTv) {
                var urlBoxFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (urlBoxFocused) palette.accent.copy(alpha = 0.25f) else palette.surfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = if (urlBoxFocused) BorderStroke(2.dp, palette.focusRing) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .tvClickable(
                            onFocusChangedState = { urlBoxFocused = it },
                            onClick = { showUrlDialog = true }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = if (urlBoxFocused) palette.focusRing else LumaTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = manifestUrlInput.ifBlank { qString(R.string.addons_url_placeholder) },
                            color = if (manifestUrlInput.isNotBlank() || urlBoxFocused) Color.White else LumaTextSecondary,
                            fontSize = 13.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = manifestUrlInput,
                    onValueChange = onUrlChange,
                    label = { Text(qString(R.string.addons_dialog_title), fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.focusRing,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = palette.surfaceElevated,
                        unfocusedContainerColor = palette.surfaceElevated,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AddonActionPill(
                    label = if (isInstalling) qString(R.string.addons_checking) else qString(R.string.addons_install_btn),
                    icon = if (isInstalling) null else Icons.Default.Add,
                    isPrimary = true,
                    isLoading = isInstalling,
                    accent = palette.accent,
                    enabled = !isInstalling && (isTv || manifestUrlInput.isNotBlank()),
                    onClick = {
                        if (manifestUrlInput.isNotBlank()) {
                            onInstallUrl(manifestUrlInput)
                        } else if (isTv) {
                            showUrlDialog = true
                        }
                    }
                )
                if (onOpenQrSetup != null) {
                    AddonActionPill(
                        label = qString(R.string.addons_phone_qr_btn),
                        icon = Icons.Default.QrCode2,
                        isPrimary = false,
                        isLoading = false,
                        accent = palette.focusRing,
                        enabled = true,
                        onClick = onOpenQrSetup
                    )
                }
                if (hasAddons) {
                    AddonActionPill(
                        label = if (isRefreshingAddons) qString(R.string.addons_refreshing) else qString(R.string.addons_refresh_btn),
                        icon = if (isRefreshingAddons) null else Icons.Default.Refresh,
                        isPrimary = false,
                        isLoading = isRefreshingAddons,
                        accent = palette.focusRing,
                        enabled = !isRefreshingAddons,
                        onClick = onRefreshAddons
                    )
                    AddonActionPill(
                        label = if (isCheckingAny) qString(R.string.addons_checking) else qString(R.string.addons_check_all),
                        icon = if (isCheckingAny) null else Icons.Default.MonitorHeart,
                        isPrimary = false,
                        isLoading = isCheckingAny,
                        accent = palette.focusRing,
                        enabled = !isCheckingAny,
                        onClick = onCheckAllHealth
                    )
                }
            }
        }
    }

    if (showUrlDialog) {
        val isTvDevice = isTvHardware(LocalContext.current)
        var draftUrl by remember(manifestUrlInput) { mutableStateOf(manifestUrlInput) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text(qString(R.string.addons_dialog_title), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = qString(if (isTvDevice) R.string.addons_dialog_tip else R.string.addons_dialog_tip_phone),
                        color = LumaTextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = draftUrl,
                        onValueChange = { draftUrl = it },
                        label = { Text("https://.../manifest.json") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.focusRing,
                            unfocusedBorderColor = palette.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                AddonActionPill(
                    label = qString(R.string.addons_install_btn),
                    icon = Icons.Default.Add,
                    isPrimary = true,
                    accent = palette.accent,
                    enabled = draftUrl.isNotBlank() && !isInstalling,
                    onClick = {
                        onUrlChange(draftUrl)
                        showUrlDialog = false
                        onInstallUrl(draftUrl)
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AddonActionPill(
    label: String,
    icon: ImageVector?,
    isPrimary: Boolean,
    accent: Color,
    enabled: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }

    val bg = when {
        !enabled -> palette.surfaceElevated.copy(alpha = 0.55f)
        focused -> palette.accent
        isPrimary -> accent
        else -> palette.surfaceElevated
    }
    val fg = when {
        !enabled -> LumaTextSecondary
        focused -> palette.onAccent
        isPrimary -> palette.onAccent
        else -> accent
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                enabled = enabled,
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = fg, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            }
            Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledAddonCard(
    addon: InstalledAddon,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    health: AddonHealthResult?,
    isCheckingHealth: Boolean,
    hiddenCatalogKeys: Set<String>,
    onToggleEnabled: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onCheckHealth: () -> Unit,
    onToggleCatalogVisibility: (String) -> Unit
) {
    val palette = LocalQuernPalette.current
    var toggleFocused by remember { mutableStateOf(false) }
    var catalogsExpanded by remember { mutableStateOf(false) }
    var expandFocused by remember { mutableStateOf(false) }

    val homeCatalogs = remember(addon) { addon.manifest.catalogs.filter { it.isHomeEligible } }

    Surface(
        color = palette.glassSurface,
        shape = RoundedCornerShape(18.dp),
        border = if (toggleFocused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = Modifier
            .fillMaxWidth()
            .frostedSheen(cornerRadius = 18.dp, intensity = if (palette.isGlossy) 0.95f else 0.7f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (toggleFocused) palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                    .tvClickable(
                        onFocusChangedState = { toggleFocused = it },
                        onClick = onToggleEnabled
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = if (addon.enabled) palette.focusRing else LumaTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${addon.manifest.name}  v${addon.manifest.version}",
                        color = LumaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = addon.manifest.description.ifBlank { addon.manifestUrl },
                        color = LumaTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = addon.enabled,
                    onCheckedChange = null,
                    colors = quernSwitchColors(onAccentSurface = false),
                    modifier = Modifier.focusProperties { canFocus = false }
                )
            }

            // Health status badge row (when checked)
            if (isCheckingHealth || health != null) {
                Spacer(Modifier.height(6.dp))
                if (isCheckingHealth) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(color = palette.focusRing, modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp)
                        Text(qString(R.string.addons_checking), color = LumaTextSecondary, fontSize = 11.sp)
                    }
                } else if (health != null) {
                    val isOk = health.reachable && health.manifestValid
                    LumaBadgeChip(
                        if (isOk) {
                            qString(R.string.addons_health_ok, health.latencyMs)
                        } else {
                            qString(R.string.addons_health_fail, health.error ?: "Error")
                        },
                        if (isOk) LumaGreen else LumaRed
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    addon.manifest.resources.take(4).forEach { res ->
                        LumaBadgeChip(res.name.uppercase(), palette.accent)
                    }
                    if (homeCatalogs.isNotEmpty()) {
                        val visibleCount = homeCatalogs.count { c ->
                            c.visibilityKey(addon.manifest.id) !in hiddenCatalogKeys
                        }
                        LumaBadgeChip("$visibleCount/${homeCatalogs.size} Catalogs", palette.focusRing)
                    }
                }

                Spacer(Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AddonIconAction(
                        icon = Icons.Default.MonitorHeart,
                        contentDescription = qString(R.string.addons_health_btn),
                        tint = LumaGreen,
                        enabled = !isCheckingHealth,
                        onClick = onCheckHealth
                    )
                    AddonIconAction(
                        icon = Icons.Default.ArrowUpward,
                        contentDescription = qString(R.string.addons_move_up),
                        tint = LumaTextSecondary,
                        enabled = canMoveUp,
                        onClick = onMoveUp
                    )
                    AddonIconAction(
                        icon = Icons.Default.ArrowDownward,
                        contentDescription = qString(R.string.addons_move_down),
                        tint = LumaTextSecondary,
                        enabled = canMoveDown,
                        onClick = onMoveDown
                    )
                    AddonIconAction(
                        icon = Icons.Default.Delete,
                        contentDescription = qString(R.string.addons_remove),
                        tint = LumaRed,
                        enabled = true,
                        onClick = onRemove
                    )
                }
            }

            // Expandable per-addon Home Catalog Visibility controls
            if (homeCatalogs.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (expandFocused) palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                        .tvClickable(
                            onFocusChangedState = { expandFocused = it },
                            onClick = { catalogsExpanded = !catalogsExpanded }
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = qString(R.string.addons_catalogs_label),
                            color = if (expandFocused) Color.White else LumaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = qString(R.string.addons_catalogs_help),
                            color = LumaTextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                    Icon(
                        imageVector = if (catalogsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = if (expandFocused) palette.focusRing else LumaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = catalogsExpanded) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        homeCatalogs.forEach { cat ->
                            val key = cat.visibilityKey(addon.manifest.id)
                            val isVisible = key !in hiddenCatalogKeys
                            var catFocused by remember { mutableStateOf(false) }
                            val firstOpt = cat.defaultHomeExtraArgs()?.values?.firstOrNull { it.isNotBlank() }
                            val baseName = cat.name?.takeIf { it.isNotBlank() } ?: cat.id
                            val label = if (!firstOpt.isNullOrBlank()) {
                                "$baseName · $firstOpt (${cat.type})"
                            } else {
                                "$baseName (${cat.type})"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (catFocused) palette.accent.copy(alpha = 0.25f) else palette.surfaceElevated)
                                    .then(
                                        if (catFocused) Modifier.border(1.5.dp, palette.focusRing, RoundedCornerShape(10.dp))
                                        else Modifier
                                    )
                                    .tvClickable(
                                        onFocusChangedState = { catFocused = it },
                                        onClick = { onToggleCatalogVisibility(key) }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = if (isVisible) palette.focusRing else LumaTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = label,
                                        color = if (isVisible) LumaTextPrimary else LumaTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Switch(
                                    checked = isVisible,
                                    onCheckedChange = null,
                                    colors = quernSwitchColors(onAccentSurface = false),
                                    modifier = Modifier.focusProperties { canFocus = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddonIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    !enabled -> palette.surfaceElevated.copy(alpha = 0.35f)
                    focused -> palette.accent
                    else -> palette.surfaceElevated
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, palette.focusRing, RoundedCornerShape(10.dp))
                else Modifier
            )
            .tvClickable(
                enabled = enabled,
                onFocusChangedState = { focused = it },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> LumaTextSecondary.copy(alpha = 0.4f)
                focused -> palette.onAccent
                else -> tint
            },
            modifier = Modifier.size(17.dp)
        )
    }
}
