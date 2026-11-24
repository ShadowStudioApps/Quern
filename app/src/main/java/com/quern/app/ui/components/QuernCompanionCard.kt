package com.quern.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.data.companion.CompanionSessionState
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Original Quern QR / Companion Sign-In Card:
 * - Large crisp QR code + short pairing code + readable URL text
 * - Numbered steps 1-2-3
 * - Live connection status (Waiting with countdown / Connected / Expired)
 * - D-Pad focusable Refresh Code action
 * Built exclusively from Quern's theme tokens and XML string resources.
 */
@Composable
fun QuernCompanionSignInCard(
    url: String,
    session: CompanionSessionState,
    onRefreshSession: () -> Unit,
    onBindServer: () -> Unit = {},
    onUnbindServer: () -> Unit = {},
    compactVertical: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalQuernPalette.current
    var nowMs by remember(session.shortCode, session.expiresAtMs) { mutableLongStateOf(System.currentTimeMillis()) }

    // Bind companion HTTP server ONLY while this card/screen is actually open
    DisposableEffect(Unit) {
        onBindServer()
        onDispose { onUnbindServer() }
    }

    LaunchedEffect(session.shortCode, session.expiresAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= session.expiresAtMs) break
            delay(1000L)
        }
        // Sessions slide with every phone request, so expiry means 10 idle minutes:
        // replace the code automatically so the QR on screen always works.
        onRefreshSession()
    }

    val isExpired = session.isExpired(nowMs)
    val remainingSec = session.remainingSeconds(nowMs)
    val countdownText = String.format(Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)

    // Never embed the pairing code in URL query strings; QR opens the clean companion URL where the user enters the short code via POST form
    val qrPayload = url

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, palette.border),
        modifier = modifier
    ) {
        if (compactVertical) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.QrCode2, null, tint = palette.focusRing, modifier = Modifier.size(18.dp))
                    Text(
                        text = qString(R.string.companion_card_title),
                        color = LumaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                CompanionStatusBadge(
                    isConnected = session.isConnected,
                    isExpired = isExpired,
                    countdownText = countdownText
                )

                Box(contentAlignment = Alignment.Center) {
                    QrCodeCard(
                        content = qrPayload,
                        size = 152.dp,
                        modifier = Modifier.alpha(if (isExpired) 0.28f else 1f)
                    )
                    if (isExpired) {
                        CompanionRefreshButton(
                            highlight = true,
                            onClick = onRefreshSession
                        )
                    }
                }

                // Short code readout
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.75f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = qString(R.string.companion_short_code_label).uppercase(),
                            color = LumaTextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = session.shortCode,
                            color = LumaTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.5.sp
                        )
                    }
                }

                // Plain URL text
                Text(
                    text = url,
                    color = palette.focusRing,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Compact numbered steps 1-2-3
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    CompanionStepRow(1, qString(R.string.companion_step_1), compact = true)
                    CompanionStepRow(2, qString(R.string.companion_step_2), compact = true)
                    CompanionStepRow(3, qString(R.string.companion_step_3), compact = true)
                }

                if (!isExpired) {
                    CompanionRefreshButton(
                        highlight = false,
                        onClick = onRefreshSession
                    )
                }
            }
        } else {
            // Wide Horizontal Card (Settings -> Accounts & Sync / Phone / Addons QR dialog)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, null, tint = palette.focusRing, modifier = Modifier.size(20.dp))
                        Text(
                            text = qString(R.string.companion_card_title),
                            color = LumaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CompanionStatusBadge(
                        isConnected = session.isConnected,
                        isExpired = isExpired,
                        countdownText = countdownText
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Large QR + Short Code
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            QrCodeCard(
                                content = qrPayload,
                                size = 152.dp,
                                modifier = Modifier.alpha(if (isExpired) 0.28f else 1f)
                            )
                            if (isExpired) {
                                CompanionRefreshButton(
                                    highlight = true,
                                    onClick = onRefreshSession
                                )
                            }
                        }

                        Surface(
                            color = palette.surfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.8f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = qString(R.string.companion_short_code_label).uppercase(),
                                    color = LumaTextSecondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = session.shortCode,
                                    color = LumaTextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 3.sp
                                )
                            }
                        }
                    }

                    // Right: Numbered Steps 1-2-3 + URL + Refresh
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompanionStepRow(1, qString(R.string.companion_step_1), compact = false)
                        CompanionStepRow(2, qString(R.string.companion_step_2), compact = false)
                        CompanionStepRow(3, qString(R.string.companion_step_3), compact = false)

                        Surface(
                            color = palette.surfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                                Text(
                                    text = qString(R.string.companion_url_label).uppercase(),
                                    color = LumaTextSecondary,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = url,
                                    color = palette.focusRing,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        CompanionRefreshButton(
                            highlight = isExpired,
                            onClick = onRefreshSession
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionStatusBadge(
    isConnected: Boolean,
    isExpired: Boolean,
    countdownText: String
) {
    val palette = LocalQuernPalette.current
    val pulse = rememberInfiniteTransition(label = "companionPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusDotAlpha"
    )

    val badgeColor = when {
        isConnected -> LumaGreen
        isExpired -> LumaRed
        else -> palette.focusRing
    }
    val label = when {
        isConnected -> qString(R.string.companion_status_connected)
        isExpired -> qString(R.string.companion_status_expired)
        else -> qString(R.string.companion_status_waiting, countdownText)
    }

    Surface(
        color = badgeColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isConnected) {
                Icon(Icons.Default.CheckCircle, null, tint = LumaGreen, modifier = Modifier.size(13.dp))
            } else if (isExpired) {
                Icon(Icons.Default.Schedule, null, tint = LumaRed, modifier = Modifier.size(13.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(dotAlpha)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
            }
            Text(
                text = label,
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun CompanionStepRow(
    stepNumber: Int,
    text: String,
    compact: Boolean
) {
    val palette = LocalQuernPalette.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 18.dp else 22.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.28f))
                .border(1.dp, palette.focusRing.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber.toString(),
                color = palette.focusRing,
                fontSize = if (compact) 10.sp else 11.5.sp,
                fontWeight = FontWeight.Black
            )
        }
        Text(
            text = text,
            color = LumaTextSecondary,
            fontSize = if (compact) 10.5.sp else 12.5.sp,
            lineHeight = if (compact) 14.sp else 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompanionRefreshButton(
    highlight: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalQuernPalette.current
    var focused by remember { mutableStateOf(false) }

    val bg = when {
        focused -> palette.accent
        highlight -> palette.accent
        else -> palette.surfaceElevated
    }
    val fg = when {
        focused || highlight -> palette.onAccent
        else -> LumaTextPrimary
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = if (focused) 2.5.dp else 1.dp,
            color = if (focused) palette.focusRing else if (highlight) palette.accent else palette.border
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .tvClickable(
                onFocusChangedState = { focused = it },
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(Icons.Default.Refresh, null, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                text = qString(R.string.companion_refresh_btn),
                color = fg,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
