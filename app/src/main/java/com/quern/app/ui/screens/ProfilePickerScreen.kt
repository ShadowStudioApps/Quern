package com.quern.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import com.quern.app.ui.components.ProfileAvatarCircle
import com.quern.app.ui.components.QuernBrandMark
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

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
    onDismiss: () -> Unit
) {
    val palette = LocalQuernPalette.current
    val activeFocus = remember { FocusRequester() }
    var pinTarget by remember { mutableStateOf<UserProfile?>(null) }

    // BACK keeps the current profile and closes the picker.
    BackHandler(enabled = pinTarget == null) { onDismiss() }

    LaunchedEffect(Unit) { runCatching { activeFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = 40.dp)
        ) {
            QuernBrandMark(size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Text("QUERN", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = qString(R.string.profile_picker_title),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(44.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                profiles.forEach { profile ->
                    var focused by remember { mutableStateOf(false) }
                    val scale = animateFloatAsState(
                        targetValue = if (focused) 1.1f else 1f,
                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                        label = "profilePickScale"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .then(if (profile.id == activeProfileId) Modifier.focusRequester(activeFocus) else Modifier)
                            .graphicsLayer {
                                scaleX = scale.value
                                scaleY = scale.value
                            }
                            .tvClickable(
                                onFocusChangedState = { focused = it },
                                onClick = {
                                    if (profile.isLocked) pinTarget = profile else onProfileChosen(profile.id)
                                }
                            )
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(
                                    width = if (focused) 4.dp else 0.dp,
                                    color = if (focused) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .padding(if (focused) 4.dp else 0.dp)
                        ) {
                            ProfileAvatarCircle(profile, size = 132.dp, fontSizeSp = 40f)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (profile.isLocked) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (focused) Color.White else LumaTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = profile.name,
                                color = if (focused) Color.White else LumaTextSecondary,
                                fontSize = 18.sp,
                                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
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
