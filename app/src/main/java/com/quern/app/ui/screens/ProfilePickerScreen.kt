package com.quern.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.rememberCoroutineScope
import com.quern.app.ui.theme.LocalQuernStrings
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.domain.model.UserProfile
import com.quern.app.ui.components.LocalQuernLayout
import com.quern.app.ui.components.ProfileAvatarCircle
import com.quern.app.ui.components.ProfileBackgroundLayer
import com.quern.app.ui.components.QuernBrandMark
import com.quern.app.ui.components.QuernLayoutClass
import com.quern.app.ui.components.profileAvatarFor
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

private const val MAX_PROFILES = 6

/** "+" tile after the profiles: opens the create-profile dialog. Same size and focus style as a profile. */
@Composable
private fun AddProfileTile(avatarSize: Dp = 132.dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "addProfileScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (focused) 1f else 0.7f
            }
            .tvClickable(onFocusChangedState = { focused = it }, onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = CircleShape
                )
                .padding(if (focused) 2.dp else 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (focused) 0.22f else 0.10f))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(avatarSize * 0.42f)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = LocalQuernStrings.current.addProfileLabel,
            color = if (focused) Color.White else LumaTextSecondary,
            fontSize = 18.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Full-screen "Who's watching?" picker shown at launch and from the rail's profile button.
 * Locked profiles go through the existing PIN keypad (with its attempt lockout) before switching.
 */
@Composable
fun ProfilePickerScreen(
    profiles: List<UserProfile>,
    activeProfileId: String,
    isAr: Boolean,
    onProfileChosen: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateProfile: (UserProfile) -> Unit
) {
    val palette = LocalQuernPalette.current
    val activeFocus = remember { FocusRequester() }
    var pinTarget by remember { mutableStateOf<UserProfile?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var focusedProfileId by remember(activeProfileId) { mutableStateOf(activeProfileId) }
    val focusedProfile = remember(profiles, focusedProfileId, activeProfileId) {
        profiles.firstOrNull { it.id == focusedProfileId }
            ?: profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.firstOrNull()
    }
    val scope = rememberCoroutineScope()

    // BACK keeps the current profile and closes the picker.
    BackHandler(enabled = pinTarget == null && !showCreate) { onDismiss() }

    LaunchedEffect(Unit) { runCatching { activeFocus.requestFocus() } }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ProfileBackgroundLayer(
            profile = focusedProfile,
            palette = palette
        )

        val isCompact = LocalQuernLayout.current == QuernLayoutClass.COMPACT
        val avatarSize = if (isCompact) 88.dp else 118.dp
        val avatarFontSp = if (isCompact) 28f else 36f
        val tileWidth = if (isCompact) 132.dp else 174.dp
        val tileHeight = if (isCompact) 168.dp else 214.dp

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = if (isCompact) 20.dp else 48.dp, top = if (isCompact) 28.dp else 40.dp)
        ) {
            QuernBrandMark(size = 21.dp)
            Spacer(Modifier.width(12.dp))
            Text("QUERN", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = qString(R.string.profile_picker_title),
                color = Color.White,
                fontSize = if (isCompact) 26.sp else 34.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(if (isCompact) 28.dp else 44.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 16.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                profiles.forEach { profile ->
                    var focused by remember { mutableStateOf(false) }
                    val scale = animateFloatAsState(
                        targetValue = if (focused) 1.07f else 1f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "profilePickScale"
                    )
                    val tileShape = RoundedCornerShape(22.dp)
                    Box(
                        modifier = Modifier
                            .size(width = tileWidth, height = tileHeight)
                            .then(if (profile.id == activeProfileId) Modifier.focusRequester(activeFocus) else Modifier)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                                alpha = if (focused) 1f else 0.84f
                            }
                            .clip(tileShape)
                            .border(
                                width = if (focused) 2.5.dp else 1.dp,
                                color = if (focused) Color.White else Color.White.copy(alpha = 0.16f),
                                shape = tileShape
                            )
                            .tvClickable(
                                onFocusChangedState = { isF ->
                                    focused = isF
                                    if (isF) focusedProfileId = profile.id
                                },
                                onClick = {
                                    if (profile.isLocked) pinTarget = profile else onProfileChosen(profile.id)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Each profile tile previews that profile's own light-painting / custom background
                        ProfileBackgroundLayer(
                            profile = profile,
                            palette = palette,
                            isTile = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .drawWithCache {
                                        val tint = Color(profileAvatarFor(profile.avatarKey)?.from ?: profile.avatarColorHex)
                                        val radius = size.minDimension * 0.61f
                                        val glow = Brush.radialGradient(listOf(tint.copy(alpha = if (focused) 0.42f else 0.12f), Color.Transparent), radius = radius)
                                        onDrawBehind { drawCircle(glow, radius) }
                                    }
                                    .clip(CircleShape)
                                    .border(
                                        width = if (focused) 2.dp else 0.dp,
                                        color = if (focused) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .padding(if (focused) 2.dp else 0.dp)
                            ) {
                                ProfileAvatarCircle(profile, size = avatarSize, fontSizeSp = avatarFontSp)
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (profile.isLocked) {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (focused) Color.White else LumaTextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = profile.name,
                                    color = if (focused) Color.White else Color.White.copy(alpha = 0.86f),
                                    fontSize = if (isCompact) 15.sp else 17.sp,
                                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (profiles.size < MAX_PROFILES) {
                    AddProfileTile(avatarSize = avatarSize, onClick = { showCreate = true })
                }
            }
        }
    }

    if (showCreate) {
        CreateProfileDialog(
            isAr = isAr,
            onDismiss = { showCreate = false },
            onCreate = { name, colorHex, pin, avatarKey, avatarUrl ->
                showCreate = false
                scope.launch { onCreateProfile(buildNewProfile(name, colorHex, pin, avatarKey, avatarUrl)) }
            }
        )
    }

    pinTarget?.let { target ->
        PinKeypadDialog(
            profileId = target.id,
            title = target.name,
            subtitle = qString(R.string.profile_picker_pin_subtitle),
            expectedPinHash = target.pinCode,
            isAr = isAr,
            onDismiss = { pinTarget = null },
            onPinAccepted = {
                pinTarget = null
                onProfileChosen(target.id)
            }
        )
    }
}
