package com.quern.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.domain.model.AppIconOption
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaTextPrimary

@Composable
internal fun AppIconGrid(
    current: AppIconOption,
    isArabic: Boolean,
    onSelect: (AppIconOption) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 620.dp -> 3
            maxWidth >= 300.dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIconOption.entries.chunked(columns).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { option ->
                        AppIconTile(option, option == current, isArabic, Modifier.weight(1f)) {
                            if (option != current) onSelect(option)
                        }
                    }
                    repeat(columns - options.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconTile(
    option: AppIconOption,
    isSelected: Boolean,
    isArabic: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    val artwork = when (option) {
        AppIconOption.SLATE_BLUE -> IconArtwork(R.mipmap.prism_icon_slate_foreground, R.mipmap.prism_icon_slate_background, R.drawable.prism_tv_banner_slate)
        AppIconOption.LIGHT_GRAY -> IconArtwork(R.mipmap.prism_icon_platinum_foreground, R.mipmap.prism_icon_platinum_background, R.drawable.prism_tv_banner_platinum)
        AppIconOption.AMOLED -> IconArtwork(R.mipmap.prism_icon_glossy_foreground, R.mipmap.prism_icon_glossy_background, R.drawable.prism_tv_banner_glossy)
        AppIconOption.OCEAN_BLUE -> IconArtwork(R.mipmap.prism_icon_ocean_foreground, R.mipmap.prism_icon_ocean_background, R.drawable.prism_tv_banner_ocean)
        AppIconOption.AURORA_GRADIENT -> IconArtwork(R.mipmap.prism_icon_aurora_foreground, R.mipmap.prism_icon_aurora_background, R.drawable.prism_tv_banner_aurora)
        AppIconOption.SUNSET_GRADIENT -> IconArtwork(R.mipmap.prism_icon_sunset_foreground, R.mipmap.prism_icon_sunset_background, R.drawable.prism_tv_banner_sunset)
    }
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) palette.onAccent else LumaTextPrimary
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .semantics { selected = isSelected }
            .tvClickable(onFocusChangedState = { focused = it }, onClick = onClick),
        color = if (focused) palette.accent else if (isSelected) palette.surfaceElevated else palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = if (focused || isSelected) BorderStroke(2.dp, palette.focusRing) else null
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isArabic) option.displayNameAr else option.displayNameEn,
                    modifier = Modifier.weight(1f),
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSelected) Icon(Icons.Default.Check, null, Modifier.size(17.dp), tint = color)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.weight(0.8f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                    Image(painterResource(artwork.background), null, Modifier.fillMaxSize())
                    Image(painterResource(artwork.foreground), null, Modifier.fillMaxSize())
                }
                Image(
                    painterResource(artwork.banner),
                    null,
                    Modifier.weight(1.8f).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

private data class IconArtwork(val foreground: Int, val background: Int, val banner: Int)
