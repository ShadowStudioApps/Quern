package com.quern.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CrueltyFree
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiNature
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.domain.model.UserProfile
import com.quern.app.ui.theme.LocalQuernPalette

/**
 * Built-in profile pictures. Original artwork made from Material icons (Apache 2.0, bundled via
 * material-icons-extended) on gradient backgrounds: no third-party characters, nothing downloaded.
 * Keys are stored in UserProfile.avatarKey and synced, so never rename or remove a key.
 */
data class ProfileAvatar(val key: String, val icon: ImageVector, val from: Long, val to: Long)

val ProfileAvatarSet: List<ProfileAvatar> = listOf(
    ProfileAvatar("paw", Icons.Filled.Pets, 0xFFF59E0B, 0xFFEA580C),
    ProfileAvatar("rocket", Icons.Filled.RocketLaunch, 0xFF6366F1, 0xFF0EA5E9),
    ProfileAvatar("gamer", Icons.Filled.SportsEsports, 0xFF8B5CF6, 0xFFEC4899),
    ProfileAvatar("football", Icons.Filled.SportsSoccer, 0xFF22C55E, 0xFF15803D),
    ProfileAvatar("basketball", Icons.Filled.SportsBasketball, 0xFFF97316, 0xFFB45309),
    ProfileAvatar("music", Icons.Filled.MusicNote, 0xFFEC4899, 0xFF9333EA),
    ProfileAvatar("star", Icons.Filled.Star, 0xFFFACC15, 0xFFF97316),
    ProfileAvatar("heart", Icons.Filled.Favorite, 0xFFF43F5E, 0xFFBE123C),
    ProfileAvatar("moon", Icons.Filled.NightsStay, 0xFF1E3A8A, 0xFF6366F1),
    ProfileAvatar("sun", Icons.Filled.WbSunny, 0xFFFBBF24, 0xFFF59E0B),
    ProfileAvatar("flower", Icons.Filled.LocalFlorist, 0xFFF472B6, 0xFFDB2777),
    ProfileAvatar("tree", Icons.Filled.Park, 0xFF10B981, 0xFF065F46),
    ProfileAvatar("bolt", Icons.Filled.Bolt, 0xFFEAB308, 0xFF4F46E5),
    ProfileAvatar("diamond", Icons.Filled.Diamond, 0xFF22D3EE, 0xFF2563EB),
    ProfileAvatar("bee", Icons.Filled.EmojiNature, 0xFFFDE047, 0xFFCA8A04),
    ProfileAvatar("bunny", Icons.Filled.CrueltyFree, 0xFFF9A8D4, 0xFFA855F7),
    ProfileAvatar("bird", Icons.Filled.FlutterDash, 0xFF38BDF8, 0xFF0369A1),
    ProfileAvatar("movie", Icons.Filled.Movie, 0xFFEF4444, 0xFF7F1D1D),
    ProfileAvatar("headphones", Icons.Filled.Headphones, 0xFF14B8A6, 0xFF0F766E),
    ProfileAvatar("palette", Icons.Filled.Palette, 0xFFA3E635, 0xFF0D9488),
    ProfileAvatar("sparkle", Icons.Filled.AutoAwesome, 0xFFC084FC, 0xFF6D28D9),
    ProfileAvatar("cake", Icons.Filled.Cake, 0xFFFDA4AF, 0xFFE11D48),
    ProfileAvatar("icecream", Icons.Filled.Icecream, 0xFFA5F3FC, 0xFFF472B6),
    ProfileAvatar("castle", Icons.Filled.Castle, 0xFF94A3B8, 0xFF334155),
    ProfileAvatar("wave", Icons.Filled.Waves, 0xFF60A5FA, 0xFF1D4ED8),
    ProfileAvatar("baby", Icons.Filled.ChildCare, 0xFFFDE68A, 0xFFFB923C),
    ProfileAvatar("anchor", Icons.Filled.Anchor, 0xFF0EA5E9, 0xFF0F172A)
)

fun profileAvatarFor(key: String?): ProfileAvatar? =
    key?.let { k -> ProfileAvatarSet.firstOrNull { it.key == k } }

/** Two-letter initials for a profile avatar ("Shadow" -> "SH", "Ali Ahmed" -> "AA"). */
fun profileInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> "?"
    }.uppercase()
}

/** Round avatar: the chosen built-in picture, or initials on the profile colour. */
@Composable
fun ProfileAvatarCircle(profile: UserProfile, size: Dp, fontSizeSp: Float) {
    AvatarDisc(
        avatar = profileAvatarFor(profile.avatarKey),
        fallbackColor = profile.avatarColorHex,
        initials = profileInitials(profile.name),
        size = size,
        fontSizeSp = fontSizeSp
    )
}

@Composable
private fun AvatarDisc(avatar: ProfileAvatar?, fallbackColor: Long, initials: String, size: Dp, fontSizeSp: Float) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (avatar != null) {
                    Modifier.background(Brush.linearGradient(listOf(Color(avatar.from), Color(avatar.to))))
                } else {
                    Modifier.background(Color(fallbackColor))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatar != null) {
            Icon(
                imageVector = avatar.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.56f)
            )
        } else {
            Text(
                text = initials,
                color = if (fallbackColor == 0xFFD3D3D3) Color.Black else Color.White,
                fontSize = fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Grid of every built-in picture plus an "initials" option (null). D-pad friendly:
 * each item is a single focus target with a clear focus ring.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileAvatarGrid(
    profileName: String,
    colorHex: Long,
    selectedKey: String?,
    itemSize: Dp = 56.dp,
    onSelect: (String?) -> Unit
) {
    val palette = LocalQuernPalette.current
    val options: List<ProfileAvatar?> = listOf<ProfileAvatar?>(null) + ProfileAvatarSet
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { option ->
            val key = option?.key
            val chosen = key == selectedKey
            var focused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(itemSize + 8.dp)
                    .border(
                        width = if (focused) 3.dp else 0.dp,
                        color = if (focused) palette.focusRing else Color.Transparent,
                        shape = CircleShape
                    )
                    .tvClickable(
                        onFocusChangedState = { focused = it },
                        onClick = { onSelect(key) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AvatarDisc(
                    avatar = option,
                    fallbackColor = colorHex,
                    initials = profileInitials(profileName.ifBlank { "?" }),
                    size = itemSize,
                    fontSizeSp = 18f
                )
                if (chosen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(itemSize * 0.36f)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                            .border(1.5.dp, Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(itemSize * 0.24f)
                        )
                    }
                }
            }
        }
    }
}
