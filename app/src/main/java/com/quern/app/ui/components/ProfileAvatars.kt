package com.quern.app.ui.components

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quern.app.R
import com.quern.app.domain.model.UserProfile
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

enum class ProfileAvatarGroup(@StringRes val labelRes: Int) {
    ANIMALS(R.string.profile_avatar_group_animals),
    CHARACTERS(R.string.profile_avatar_group_characters),
    PEOPLE(R.string.profile_avatar_group_people)
}

private enum class AvatarPickerTab(@StringRes val labelRes: Int) {
    ALL(R.string.profile_avatar_group_all),
    ANIMALS(R.string.profile_avatar_group_animals),
    CHARACTERS(R.string.profile_avatar_group_characters),
    PEOPLE(R.string.profile_avatar_group_people),
    MONOGRAM(R.string.profile_avatar_group_monogram)
}

/**
 * Bundled profile picture rendered on Quern's obsidian "glass medallion" disc.
 *
 * Artwork sources (all bundled offline as 192x192 WebP in drawable-nodpi, see docs/avatar-sources.md):
 * - Animals (16) & Characters (14): Microsoft Fluent Emoji 3D (MIT License)
 * - People (15): DiceBear Lorelei (CC0 1.0, generated offline in full colour with fixed seeds)
 */
data class ProfileAvatar(
    val key: String,
    @DrawableRes val drawableRes: Int,
    val group: ProfileAvatarGroup,
    val glowHex: Long
) {
    /** Alias preserved for ProfilePickerScreen focus aura tinting. */
    val from: Long get() = glowHex
}

/** 12 curated obsidian-glass tints for the serif Monogram option (includes all 6 legacy profile colours). */
val MonogramTintPalette: List<Long> = listOf(
    0xFFD3D3D3L, // Obsidian Silver
    0xFFD97736L, // Amber Ember
    0xFF8C3B5EL, // Burgundy Velvet
    0xFF0E7C7BL, // Deep Teal
    0xFF345372L, // Sapphire Slate
    0xFF10B981L, // Emerald Jade
    0xFF4F46E5L, // Midnight Indigo
    0xFF8B5CF6L, // Royal Violet
    0xFF06B6D4L, // Glacier Cyan
    0xFFEC4899L, // Rose Quartz
    0xFFD9A441L, // Champagne Gold
    0xFFBE3455L  // Crimson Velvet
)

val ProfileAvatarSet: List<ProfileAvatar> = listOf(
    // Animals (16 — Fluent Emoji 3D, MIT)
    ProfileAvatar("fox", R.drawable.av_fox, ProfileAvatarGroup.ANIMALS, 0xFFEA580C),
    ProfileAvatar("cat", R.drawable.av_cat, ProfileAvatarGroup.ANIMALS, 0xFFF59E0B),
    ProfileAvatar("dog", R.drawable.av_dog, ProfileAvatarGroup.ANIMALS, 0xFFD97706),
    ProfileAvatar("lion", R.drawable.av_lion, ProfileAvatarGroup.ANIMALS, 0xFFEAB308),
    ProfileAvatar("tiger", R.drawable.av_tiger, ProfileAvatarGroup.ANIMALS, 0xFFF97316),
    ProfileAvatar("panda", R.drawable.av_panda, ProfileAvatarGroup.ANIMALS, 0xFF10B981),
    ProfileAvatar("koala", R.drawable.av_koala, ProfileAvatarGroup.ANIMALS, 0xFF64748B),
    ProfileAvatar("bear", R.drawable.av_bear, ProfileAvatarGroup.ANIMALS, 0xFFB45309),
    ProfileAvatar("wolf", R.drawable.av_wolf, ProfileAvatarGroup.ANIMALS, 0xFF6366F1),
    ProfileAvatar("rabbit", R.drawable.av_rabbit, ProfileAvatarGroup.ANIMALS, 0xFFEC4899),
    ProfileAvatar("owl", R.drawable.av_owl, ProfileAvatarGroup.ANIMALS, 0xFFA16207),
    ProfileAvatar("penguin", R.drawable.av_penguin, ProfileAvatarGroup.ANIMALS, 0xFF06B6D4),
    ProfileAvatar("dragon", R.drawable.av_dragon, ProfileAvatarGroup.ANIMALS, 0xFF059669),
    ProfileAvatar("unicorn", R.drawable.av_unicorn, ProfileAvatarGroup.ANIMALS, 0xFFA855F7),
    ProfileAvatar("octopus", R.drawable.av_octopus, ProfileAvatarGroup.ANIMALS, 0xFFF43F5E),
    ProfileAvatar("frog", R.drawable.av_frog, ProfileAvatarGroup.ANIMALS, 0xFF22C55E),

    // Characters (14 — Fluent Emoji 3D, MIT, varied genders & skin tones)
    ProfileAvatar("astronaut", R.drawable.av_astronaut, ProfileAvatarGroup.CHARACTERS, 0xFF3B82F6),
    ProfileAvatar("robot", R.drawable.av_robot, ProfileAvatarGroup.CHARACTERS, 0xFF0EA5E9),
    ProfileAvatar("mage", R.drawable.av_mage, ProfileAvatarGroup.CHARACTERS, 0xFF8B5CF6),
    ProfileAvatar("ninja", R.drawable.av_ninja, ProfileAvatarGroup.CHARACTERS, 0xFFEF4444),
    ProfileAvatar("superhero", R.drawable.av_superhero, ProfileAvatarGroup.CHARACTERS, 0xFF2563EB),
    ProfileAvatar("vampire", R.drawable.av_vampire, ProfileAvatarGroup.CHARACTERS, 0xFFDC2626),
    ProfileAvatar("genie", R.drawable.av_genie, ProfileAvatarGroup.CHARACTERS, 0xFF06B6D4),
    ProfileAvatar("elf", R.drawable.av_elf, ProfileAvatarGroup.CHARACTERS, 0xFF14B8A6),
    ProfileAvatar("detective", R.drawable.av_detective, ProfileAvatarGroup.CHARACTERS, 0xFF6366F1),
    ProfileAvatar("cook", R.drawable.av_cook, ProfileAvatarGroup.CHARACTERS, 0xFFF59E0B),
    ProfileAvatar("scientist", R.drawable.av_scientist, ProfileAvatarGroup.CHARACTERS, 0xFF10B981),
    ProfileAvatar("artist", R.drawable.av_artist, ProfileAvatarGroup.CHARACTERS, 0xFFEC4899),
    ProfileAvatar("alien", R.drawable.av_alien, ProfileAvatarGroup.CHARACTERS, 0xFF22C55E),
    ProfileAvatar("ghost", R.drawable.av_ghost, ProfileAvatarGroup.CHARACTERS, 0xFFA855F7),

    // People (15 — DiceBear Lorelei (8) & Notionists (7), CC0 1.0, offline fixed seeds)
    ProfileAvatar("person_nora", R.drawable.av_person_nora, ProfileAvatarGroup.PEOPLE, 0xFF6366F1),
    ProfileAvatar("person_tariq", R.drawable.av_person_tariq, ProfileAvatarGroup.PEOPLE, 0xFF0EA5E9),
    ProfileAvatar("person_layla", R.drawable.av_person_layla, ProfileAvatarGroup.PEOPLE, 0xFFD97706),
    ProfileAvatar("person_amara", R.drawable.av_person_amara, ProfileAvatarGroup.PEOPLE, 0xFFEAB308),
    ProfileAvatar("person_clara", R.drawable.av_person_clara, ProfileAvatarGroup.PEOPLE, 0xFFF43F5E),
    ProfileAvatar("person_kenji", R.drawable.av_person_kenji, ProfileAvatarGroup.PEOPLE, 0xFF14B8A6),
    ProfileAvatar("person_samira", R.drawable.av_person_samira, ProfileAvatarGroup.PEOPLE, 0xFFA855F7),
    ProfileAvatar("person_zayd", R.drawable.av_person_zayd, ProfileAvatarGroup.PEOPLE, 0xFF10B981),
    ProfileAvatar("person_hana", R.drawable.av_person_hana, ProfileAvatarGroup.PEOPLE, 0xFF38BDF8),
    ProfileAvatar("person_karim", R.drawable.av_person_karim, ProfileAvatarGroup.PEOPLE, 0xFFD9A441),
    ProfileAvatar("person_maya", R.drawable.av_person_maya, ProfileAvatarGroup.PEOPLE, 0xFFEC4899),
    ProfileAvatar("person_adam", R.drawable.av_person_adam, ProfileAvatarGroup.PEOPLE, 0xFF3B82F6),
    ProfileAvatar("person_yara", R.drawable.av_person_yara, ProfileAvatarGroup.PEOPLE, 0xFF8B5CF6),
    ProfileAvatar("person_leo", R.drawable.av_person_leo, ProfileAvatarGroup.PEOPLE, 0xFF22C55E),
    ProfileAvatar("person_sara", R.drawable.av_person_sara, ProfileAvatarGroup.PEOPLE, 0xFFF97316)
)

private val ProfileAvatarByKey: Map<String, ProfileAvatar> =
    ProfileAvatarSet.associateBy { it.key }

/**
 * Backward-compatibility table mapping all 27 legacy persisted avatar keys (plus intermediate keys)
 * to their closest current bundled avatar so existing profiles and synced accounts never break.
 */
private val LegacyAvatarKeyMap: Map<String, String> = mapOf(
    "paw" to "dog",
    "rocket" to "astronaut",
    "gamer" to "robot",
    "football" to "lion",
    "basketball" to "tiger",
    "music" to "person_samira",
    "star" to "unicorn",
    "heart" to "cat",
    "moon" to "owl",
    "sun" to "fox",
    "flower" to "person_layla",
    "tree" to "koala",
    "bolt" to "superhero",
    "diamond" to "genie",
    "bee" to "frog",
    "bunny" to "rabbit",
    "bird" to "penguin",
    "movie" to "detective",
    "headphones" to "person_kenji",
    "palette" to "artist",
    "sparkle" to "mage",
    "cake" to "cook",
    "icecream" to "panda",
    "castle" to "dragon",
    "wave" to "octopus",
    "baby" to "bear",
    "anchor" to "wolf",
    // Intermediate keys
    "person_classic" to "person_nora",
    "person_glasses" to "person_kenji",
    "person_shades" to "person_adam",
    "person_cap" to "person_leo",
    "person_bob" to "person_clara",
    "person_bun" to "person_yara",
    "whale" to "octopus",
    "butterfly" to "unicorn",
    "crown" to "lion",
    "aurora" to "elf",
    "prism" to "scientist",
    "orbit" to "astronaut",
    "compass" to "detective"
)

fun profileAvatarFor(key: String?): ProfileAvatar? {
    if (key.isNullOrBlank()) return null
    return ProfileAvatarByKey[key] ?: LegacyAvatarKeyMap[key]?.let { ProfileAvatarByKey[it] }
}

/** Validates and trims an optional custom avatar URL (strictly https:// with a non-empty host). */
fun normalizeCustomAvatarUrl(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 } ?: return null
    if (!trimmed.startsWith("https://", ignoreCase = true)) return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.trim().orEmpty()
    if (scheme != "https" || host.isEmpty() || !host.contains('.')) return null
    return trimmed
}

/** Two-letter initials for a profile avatar ("Shadow" -> "SH", "Ali Ahmed" -> "AA"). */
fun profileInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> "?"
    }.uppercase()
}

/**
 * Round Quern "glass medallion" avatar:
 * 1) Custom HTTPS URL (circle-cropped at ~256 px via Coil, falling back to serif Monogram on error)
 * 2) Bundled 192x192 WebP art filling ~85% on a dark glass disc with per-avatar inner glow
 * 3) Serif Monogram on a dark glass disc tinted by [UserProfile.avatarColorHex]
 */
@Composable
fun ProfileAvatarCircle(profile: UserProfile, size: Dp, fontSizeSp: Float) {
    AvatarDisc(
        avatar = profileAvatarFor(profile.avatarKey),
        customUrl = normalizeCustomAvatarUrl(profile.avatarUrl),
        fallbackColor = profile.avatarColorHex,
        initials = profileInitials(profile.name),
        size = size,
        fontSizeSp = fontSizeSp
    )
}

@Composable
private fun AvatarDisc(
    avatar: ProfileAvatar?,
    customUrl: String?,
    fallbackColor: Long,
    initials: String,
    size: Dp,
    fontSizeSp: Float
) {
    val context = LocalContext.current
    val ringColor = Color(fallbackColor)
    val glowColor = if (avatar != null && customUrl == null) Color(avatar.glowHex) else ringColor
    var customImageLoaded by remember(customUrl) { mutableStateOf(false) }
    var customImageFailed by remember(customUrl) { mutableStateOf(false) }
    val showCustomImage = customUrl != null && !customImageFailed

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .drawWithCache {
                val obsidianBase = Brush.verticalGradient(
                    colors = listOf(Color(0xFF151923), Color(0xFF0A0C12), Color(0xFF07080D))
                )
                val innerGlowAlpha = if (avatar == null && !showCustomImage) 0.44f else 0.34f
                val innerGlow = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = innerGlowAlpha),
                        glowColor.copy(alpha = innerGlowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(this.size.width * 0.5f, this.size.height * 0.56f),
                    radius = this.size.minDimension * 0.56f
                )
                val topHighlight = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(this.size.width * 0.5f, this.size.height * 0.12f),
                    radius = this.size.minDimension * 0.52f
                )
                val ringStroke = (this.size.minDimension * 0.052f).coerceIn(1.4.dp.toPx(), 3.dp.toPx())
                val innerRimStroke = 1.dp.toPx()
                val innerRimBrush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f))
                )
                val innerRimRadius = (this.size.minDimension - ringStroke * 2f - innerRimStroke) / 2f
                val ringBrush = Brush.linearGradient(
                    colors = listOf(
                        lerp(ringColor, Color.White, 0.34f).copy(alpha = 0.90f),
                        ringColor.copy(alpha = 0.62f)
                    ),
                    start = Offset.Zero,
                    end = Offset(this.size.width, this.size.height)
                )
                val ringRadius = (this.size.minDimension - ringStroke) / 2f
                val innerRimStyle = Stroke(width = innerRimStroke)
                val ringStyle = Stroke(width = ringStroke)

                onDrawWithContent {
                    drawCircle(brush = obsidianBase)
                    drawCircle(brush = innerGlow)
                    drawCircle(brush = topHighlight)
                    drawContent()
                    drawCircle(brush = innerRimBrush, radius = innerRimRadius, style = innerRimStyle)
                    drawCircle(brush = ringBrush, radius = ringRadius, style = ringStyle)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Foreground content: custom HTTPS photo, bundled WebP art (~85%), or serif Monogram
        if (showCustomImage) {
            if (!customImageLoaded) {
                MonogramInitialsText(initials = initials, glowColor = glowColor, fontSizeSp = fontSizeSp)
            }
            val request = remember(customUrl) {
                ImageRequest.Builder(context)
                    .data(customUrl)
                    .size(256, 256)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { customImageLoaded = true },
                onError = { customImageFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding((size * 0.04f).coerceIn(1.5.dp, 3.5.dp))
                    .clip(CircleShape)
            )
        } else if (avatar != null && customUrl == null) {
            Image(
                painter = painterResource(id = avatar.drawableRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size * 0.85f)
            )
        } else {
            MonogramInitialsText(initials = initials, glowColor = glowColor, fontSizeSp = fontSizeSp)
        }
    }
}

@Composable
private fun MonogramInitialsText(
    initials: String,
    glowColor: Color,
    fontSizeSp: Float
) {
    Text(
        text = initials,
        color = lerp(Color.White, glowColor, 0.14f),
        fontSize = fontSizeSp.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        maxLines = 1
    )
}

/**
 * Quern avatar picker:
 * - Scrollable tab row: All / Animals / Characters / People / Monogram (selected tab stays scrolled into view)
 * - Big glass-medallion previews in a D-pad friendly grid with check badge on the active choice
 * - Monogram tab with live serif preview + 12 curated obsidian-glass tint choices
 * - Optional custom HTTPS picture URL field below the picker (on TV hardware, keyboard opens ONLY on OK)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileAvatarGrid(
    profileName: String,
    colorHex: Long,
    selectedKey: String?,
    customUrl: String? = null,
    itemSize: Dp = 72.dp,
    onSelect: (String?) -> Unit,
    onSelectMonogram: (Long) -> Unit = { onSelect(null) },
    onApplyCustomUrl: ((String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
    val isTv = remember(context) { isTvHardware(context) }

    var activeTab by remember { mutableStateOf(AvatarPickerTab.ALL) }
    val tabListState = rememberLazyListState()
    LaunchedEffect(activeTab) {
        tabListState.animateScrollToItem(activeTab.ordinal)
    }

    val canonicalSelectedKey = remember(selectedKey) { profileAvatarFor(selectedKey)?.key }
    val validCustomUrl = remember(customUrl) { normalizeCustomAvatarUrl(customUrl) }
    val displayGroups = remember(activeTab) {
        when (activeTab) {
            AvatarPickerTab.ALL -> ProfileAvatarGroup.entries
            AvatarPickerTab.ANIMALS -> listOf(ProfileAvatarGroup.ANIMALS)
            AvatarPickerTab.CHARACTERS -> listOf(ProfileAvatarGroup.CHARACTERS)
            AvatarPickerTab.PEOPLE -> listOf(ProfileAvatarGroup.PEOPLE)
            AvatarPickerTab.MONOGRAM -> emptyList()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Scrollable tab row: All / Animals / Characters / People / Monogram
        LazyRow(
            state = tabListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(AvatarPickerTab.entries, key = { _, tab -> tab.name }) { _, tab ->
                AvatarGroupTabPill(
                    label = qString(tab.labelRes),
                    selected = activeTab == tab,
                    isCompact = isCompact,
                    onClick = { activeTab = tab }
                )
            }
        }

        if (activeTab == AvatarPickerTab.MONOGRAM) {
            MonogramTabContent(
                profileName = profileName,
                selectedColorHex = colorHex,
                isMonogramActive = canonicalSelectedKey == null && validCustomUrl == null,
                itemSize = (itemSize * 0.86f).coerceIn(50.dp, 64.dp),
                onSelectTint = onSelectMonogram
            )
        } else {
            displayGroups.forEach { grp ->
                val groupAvatars = remember(grp) { ProfileAvatarSet.filter { it.group == grp } }
                if (activeTab == AvatarPickerTab.ALL) {
                    Text(
                        text = qString(grp.labelRes),
                        color = LumaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    groupAvatars.forEach { option ->
                        val chosen = validCustomUrl == null && option.key == canonicalSelectedKey
                        var focused by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (focused) 1.07f else 1f,
                            animationSpec = tween(140, easing = FastOutSlowInEasing),
                            label = "avatarItemScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(itemSize + 10.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .tvClickable(
                                    onFocusChangedState = { focused = it },
                                    onClick = { onSelect(option.key) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(itemSize + 8.dp)
                                    .border(
                                        width = if (focused) 2.8.dp else if (chosen) 2.dp else 0.dp,
                                        color = when {
                                            focused -> palette.focusRing
                                            chosen -> Color.White.copy(alpha = 0.85f)
                                            else -> Color.Transparent
                                        },
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarDisc(
                                    avatar = option,
                                    customUrl = null,
                                    fallbackColor = colorHex,
                                    initials = "",
                                    size = itemSize,
                                    fontSizeSp = (itemSize.value * 0.34f)
                                )
                            }
                            if (chosen) {
                                AvatarSelectedBadge(
                                    modifier = Modifier.align(Alignment.BottomEnd),
                                    badgeSize = (itemSize * 0.32f).coerceIn(18.dp, 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (onApplyCustomUrl != null) {
            CustomAvatarUrlCard(
                profileName = profileName,
                colorHex = colorHex,
                currentUrl = validCustomUrl,
                isTv = isTv,
                isCompact = isCompact,
                onApplyUrl = onApplyCustomUrl
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonogramTabContent(
    profileName: String,
    selectedColorHex: Long,
    isMonogramActive: Boolean,
    itemSize: Dp,
    onSelectTint: (Long) -> Unit
) {
    val palette = LocalQuernPalette.current
    val initials = remember(profileName) { profileInitials(profileName.ifBlank { "Q" }) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                AvatarDisc(
                    avatar = null,
                    customUrl = null,
                    fallbackColor = selectedColorHex,
                    initials = initials,
                    size = 76.dp,
                    fontSizeSp = 28f
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = qString(R.string.profile_avatar_group_monogram),
                        color = LumaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = qString(R.string.profile_avatar_monogram_hint),
                        color = LumaTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MonogramTintPalette.forEach { tintHex ->
                val chosen = isMonogramActive && tintHex == selectedColorHex
                var focused by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (focused) 1.07f else 1f,
                    animationSpec = tween(140, easing = FastOutSlowInEasing),
                    label = "monogramTintScale"
                )
                Box(
                    modifier = Modifier
                        .size(itemSize + 8.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .tvClickable(
                            onFocusChangedState = { focused = it },
                            onClick = { onSelectTint(tintHex) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(itemSize + 6.dp)
                            .border(
                                width = if (focused) 2.8.dp else if (chosen) 2.dp else 0.dp,
                                color = when {
                                    focused -> palette.focusRing
                                    chosen -> Color.White.copy(alpha = 0.85f)
                                    else -> Color.Transparent
                                },
                                shape = CircleShape
                            )
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarDisc(
                            avatar = null,
                            customUrl = null,
                            fallbackColor = tintHex,
                            initials = initials,
                            size = itemSize,
                            fontSizeSp = itemSize.value * 0.36f
                        )
                    }
                    if (chosen) {
                        AvatarSelectedBadge(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            badgeSize = 20.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomAvatarUrlCard(
    profileName: String,
    colorHex: Long,
    currentUrl: String?,
    isTv: Boolean,
    isCompact: Boolean,
    onApplyUrl: (String?) -> Unit
) {
    val palette = LocalQuernPalette.current
    var draft by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }
    var showError by remember { mutableStateOf(false) }
    var showTvInputDialog by remember { mutableStateOf(false) }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentUrl != null) {
                    AvatarDisc(
                        avatar = null,
                        customUrl = currentUrl,
                        fallbackColor = colorHex,
                        initials = profileInitials(profileName.ifBlank { "Q" }),
                        size = 42.dp,
                        fontSizeSp = 15f
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = LumaTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = qString(R.string.profile_avatar_custom_url_title),
                        color = LumaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = qString(R.string.profile_avatar_custom_url_privacy),
                        color = LumaTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isTv) {
                // On TV hardware, keyboard opens ONLY when user presses OK on this row.
                var rowFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (rowFocused) palette.accent.copy(alpha = 0.28f) else palette.glassElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        width = if (rowFocused) 2.dp else 1.dp,
                        color = if (rowFocused) palette.focusRing else palette.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .tvClickable(
                            onFocusChangedState = { rowFocused = it },
                            onClick = {
                                draft = currentUrl.orEmpty()
                                showError = false
                                showTvInputDialog = true
                            }
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = currentUrl ?: qString(R.string.profile_avatar_custom_url_tv_hint),
                            color = if (currentUrl != null) LumaTextPrimary else LumaTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentUrl != null) {
                            Spacer(Modifier.width(8.dp))
                            AvatarSelectedBadge(badgeSize = 18.dp)
                        }
                    }
                }
                if (currentUrl != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AvatarGroupTabPill(
                            label = qString(R.string.profile_avatar_custom_url_clear),
                            selected = false,
                            isCompact = isCompact,
                            onClick = {
                                draft = ""
                                showError = false
                                onApplyUrl(null)
                            }
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        showError = false
                    },
                    placeholder = {
                        Text(
                            text = qString(R.string.profile_avatar_custom_url_placeholder),
                            color = LumaTextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    isError = showError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val normalized = normalizeCustomAvatarUrl(draft)
                            if (draft.isBlank()) {
                                showError = false
                                onApplyUrl(null)
                            } else if (normalized != null) {
                                showError = false
                                onApplyUrl(normalized)
                            } else {
                                showError = true
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.focusRing,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (showError) {
                    Text(
                        text = qString(R.string.profile_avatar_custom_url_invalid),
                        color = Color(0xFFF87171),
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarGroupTabPill(
                        label = qString(R.string.profile_avatar_custom_url_apply),
                        selected = true,
                        isCompact = isCompact,
                        onClick = {
                            val normalized = normalizeCustomAvatarUrl(draft)
                            if (normalized != null) {
                                showError = false
                                onApplyUrl(normalized)
                            } else {
                                showError = true
                            }
                        }
                    )
                    if (currentUrl != null || draft.isNotBlank()) {
                        AvatarGroupTabPill(
                            label = qString(R.string.profile_avatar_custom_url_clear),
                            selected = false,
                            isCompact = isCompact,
                            onClick = {
                                draft = ""
                                showError = false
                                onApplyUrl(null)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showTvInputDialog) {
        AlertDialog(
            onDismissRequest = { showTvInputDialog = false },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    text = qString(R.string.profile_avatar_custom_url_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            showError = false
                        },
                        placeholder = {
                            Text(
                                text = qString(R.string.profile_avatar_custom_url_placeholder),
                                color = LumaTextSecondary,
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        isError = showError,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.focusRing,
                            unfocusedBorderColor = palette.border,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showError) {
                        Text(
                            text = qString(R.string.profile_avatar_custom_url_invalid),
                            color = Color(0xFFF87171),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = qString(R.string.profile_avatar_custom_url_privacy),
                        color = LumaTextSecondary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (draft.isBlank()) {
                            showError = false
                            showTvInputDialog = false
                            onApplyUrl(null)
                        } else {
                            val normalized = normalizeCustomAvatarUrl(draft)
                            if (normalized != null) {
                                showError = false
                                showTvInputDialog = false
                                onApplyUrl(normalized)
                            } else {
                                showError = true
                            }
                        }
                    }
                ) {
                    Text(qString(R.string.profile_avatar_custom_url_apply), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTvInputDialog = false }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AvatarSelectedBadge(
    modifier: Modifier = Modifier,
    badgeSize: Dp = 20.dp
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(LumaGreen)
            .border(1.5.dp, Color.Black, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(badgeSize * 0.62f)
        )
    }
}

@Composable
private fun AvatarGroupTabPill(
    label: String,
    selected: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> palette.accent
        selected -> palette.accent.copy(alpha = 0.78f)
        else -> palette.glassElevated
    }
    val fg = if (focused || selected) palette.onAccent else LumaTextPrimary
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = if (focused) BorderStroke(2.dp, palette.focusRing) else null,
        modifier = Modifier
            .then(if (isCompact) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = if (isCompact) 11.dp else 7.dp
            )
        ) {
            Text(
                text = label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
