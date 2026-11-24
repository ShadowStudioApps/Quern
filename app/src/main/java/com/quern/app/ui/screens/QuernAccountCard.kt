package com.quern.app.ui.screens

import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.data.companion.DeviceLinkSessionState
import com.quern.app.data.sync.AccountCrypto
import com.quern.app.data.sync.AccountError
import com.quern.app.data.sync.AccountUiMessage
import com.quern.app.data.sync.DeviceLinkCrypto
import com.quern.app.data.sync.QuernAccountState
import com.quern.app.data.sync.SyncStatus
import com.quern.app.ui.components.QrCodeCard
import com.quern.app.ui.components.QuernAlertDialog as AlertDialog
import com.quern.app.ui.components.isTvHardware
import com.quern.app.ui.components.tvClickable
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString
import kotlinx.coroutines.delay
import java.util.Locale

/** Everything the account card needs, bundled so it can pass through the settings layers. */
class AccountUi(
    val state: QuernAccountState,
    val busy: Boolean,
    val message: AccountUiMessage?,
    val onSignIn: (email: String, password: String) -> Unit,
    val onSignUp: (email: String, password: String) -> Unit,
    val onResend: (email: String) -> Unit,
    val onSignOut: (discardUnsynced: Boolean) -> Unit,
    val onDelete: () -> Unit,
    val sync: SyncStatus = SyncStatus(),
    val onSyncNow: () -> Unit = {},
    val deviceLink: DeviceLinkSessionState = DeviceLinkSessionState(),
    val deviceLinkUrl: () -> String = { "" },
    val onStartDeviceLink: (deviceName: String) -> Unit = {},
    val onStopDeviceLink: () -> Unit = {},
    val onRefreshDeviceLink: (deviceName: String) -> Unit = {}
)

@Composable
private fun syncStatusText(sync: SyncStatus): String = when {
    sync.running -> qString(R.string.qa_sync_running)
    sync.keyMismatch -> qString(R.string.qa_sync_key_mismatch)
    sync.lastError != null -> qString(R.string.qa_sync_failed)
    sync.lastSyncAt > 0L && System.currentTimeMillis() - sync.lastSyncAt < DateUtils.MINUTE_IN_MILLIS ->
        qString(R.string.qa_sync_just_now)
    sync.lastSyncAt > 0L -> qString(
        R.string.qa_sync_last,
        DateUtils.getRelativeTimeSpanString(sync.lastSyncAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    )
    else -> qString(R.string.qa_sync_never)
}

@Composable
private fun accountMessageText(message: AccountUiMessage, state: QuernAccountState): String = when (message) {
    AccountUiMessage.SignedIn -> qString(R.string.qa_msg_signed_in)
    AccountUiMessage.SignedUp ->
        if (state.isSignedIn) qString(R.string.qa_msg_signed_up) else qString(R.string.qa_msg_signed_up_confirm)
    AccountUiMessage.ConfirmationResent -> qString(R.string.qa_msg_resent)
    AccountUiMessage.SavingBeforeSignOut -> qString(R.string.qa_saving_before_sign_out)
    AccountUiMessage.SignOutSaveFailed -> qString(R.string.qa_sign_out_save_failed)
    AccountUiMessage.SignedOut -> qString(R.string.qa_msg_signed_out)
    AccountUiMessage.Deleted -> qString(R.string.qa_msg_deleted)
    is AccountUiMessage.Failed -> when (message.reason) {
        AccountError.INVALID_EMAIL -> qString(R.string.qa_err_invalid_email)
        AccountError.WEAK_PASSWORD -> qString(R.string.qa_err_weak_password, AccountCrypto.MIN_PASSWORD_LENGTH)
        AccountError.WRONG_CREDENTIALS -> qString(R.string.qa_err_wrong)
        AccountError.EMAIL_NOT_CONFIRMED -> qString(R.string.qa_err_not_confirmed)
        AccountError.RATE_LIMITED -> qString(R.string.qa_err_rate)
        AccountError.NETWORK -> qString(R.string.qa_err_network)
        AccountError.SERVER -> qString(R.string.qa_err_server)
    }
}

/** Quern account on Shadow Studio's server: QR sign-in on TV, sign up / sign in / sign out / delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuernAccountCard(account: AccountUi, isTv: Boolean = false) {
    val palette = LocalQuernPalette.current
    val isTvDevice = isTvHardware(LocalContext.current)
    val state = account.state
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.focusRing,
        // Theme borders can be transparent; input fields always need a visible outline.
        unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
        focusedLabelColor = palette.focusRing,
        unfocusedLabelColor = LumaTextSecondary,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White
    )
    var email by remember(state.email, state.awaitingEmailConfirmation) {
        mutableStateOf(state.awaitingEmailConfirmation ?: state.email)
    }
    var password by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showPhoneEmailHelp by remember { mutableStateOf(false) }
    var dismissedSaveWarning by remember(account.message) { mutableStateOf(false) }

    SettingSectionCard(title = qString(R.string.qa_title)) {
        if (state.isSignedIn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CloudDone, null, tint = LumaGreen, modifier = Modifier.size(26.dp))
                Column {
                    Text(
                        qString(R.string.qa_signed_in_as, state.email),
                        color = LumaTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(qString(R.string.qa_signed_in_desc), color = LumaTextSecondary, fontSize = 12.sp)
                    Text(
                        syncStatusText(account.sync),
                        color = if (account.sync.keyMismatch || account.sync.lastError != null) LumaRed else LumaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectablePillOption(label = qString(R.string.qa_sync_now), selected = true) {
                    if (!account.sync.running) account.onSyncNow()
                }
                SelectablePillOption(label = qString(R.string.qa_sign_out), selected = false) {
                    if (!account.busy) confirmSignOut = true
                }
                SelectablePillOption(label = qString(R.string.qa_delete), selected = false) {
                    if (!account.busy) confirmDelete = true
                }
            }
        } else {
            Text(qString(R.string.qa_desc_signed_out), color = LumaTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(qString(R.string.qa_privacy_note), color = LumaTextSecondary, fontSize = 12.sp)

            if (isTvDevice) {
                Spacer(Modifier.height(12.dp))
                TvDeviceLinkQrPanel(account = account)
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { showPhoneEmailHelp = !showPhoneEmailHelp }) {
                    Text(qString(R.string.qa_phone_email_link), color = palette.focusRing, fontSize = 13.sp)
                }
                if (showPhoneEmailHelp) {
                    HelpTextLine(qString(R.string.qa_phone_email_help))
                }
            }

            if (!isTvDevice) {
                state.awaitingEmailConfirmation?.let { pending ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        qString(R.string.qa_awaiting, pending),
                        color = palette.focusRing,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(qString(R.string.qa_email)) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(qString(R.string.qa_password, AccountCrypto.MIN_PASSWORD_LENGTH)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectablePillOption(label = qString(R.string.qa_sign_in), selected = true) {
                        if (!account.busy) account.onSignIn(email, password)
                    }
                    SelectablePillOption(label = qString(R.string.qa_create), selected = false) {
                        if (!account.busy) account.onSignUp(email, password)
                    }
                    if (state.awaitingEmailConfirmation != null) {
                        SelectablePillOption(label = qString(R.string.qa_resend), selected = false) {
                            if (!account.busy) account.onResend(email)
                        }
                    }
                }
            }
        }

        val message = account.message
        when {
            account.busy -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (message == AccountUiMessage.SavingBeforeSignOut)
                        qString(R.string.qa_saving_before_sign_out) else qString(R.string.qa_working),
                    color = LumaTextSecondary,
                    fontSize = 13.sp
                )
            }
            message != null && message != AccountUiMessage.SignOutSaveFailed -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    accountMessageText(message, state),
                    color = if (message is AccountUiMessage.Failed) Color(0xFFFF8A80) else LumaGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    val saveFailed = account.message == AccountUiMessage.SignOutSaveFailed && !dismissedSaveWarning
    if (confirmSignOut || saveFailed) {
        val dismiss = {
            confirmSignOut = false
            dismissedSaveWarning = true
        }
        AlertDialog(
            onDismissRequest = dismiss,
            containerColor = palette.surfaceElevated,
            title = { Text(qString(R.string.qa_sign_out), color = Color.White) },
            text = {
                Text(
                    qString(when {
                        saveFailed -> R.string.qa_sign_out_save_failed
                        isTv -> R.string.qa_sign_out_tv_confirm_body
                        else -> R.string.qa_sign_out_confirm_body
                    }),
                    color = LumaTextSecondary
                )
            },
            confirmButton = {
                SelectablePillOption(
                    label = qString(if (saveFailed) R.string.qa_sign_out_anyway else R.string.qa_sign_out),
                    selected = false
                ) {
                    dismiss()
                    account.onSignOut(saveFailed)
                }
            },
            dismissButton = {
                SelectablePillOption(label = qString(R.string.common_cancel), selected = false) {
                    dismiss()
                }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = palette.surfaceElevated,
            title = { Text(qString(R.string.qa_delete_confirm_title), color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text(qString(R.string.qa_delete_confirm_body), color = LumaTextSecondary, fontSize = 13.sp) },
            confirmButton = {
                SelectablePillOption(label = qString(R.string.qa_delete_confirm_btn), selected = false) {
                    confirmDelete = false
                    account.onDelete()
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        )
    }
}

/**
 * TV-only QR sign-in panel inside the signed-out Account card:
 * - Opens a 5-minute ephemeral EC P-256 session while this composable is on screen (closes and wipes
 *   the private key immediately on dispose or after successful sign-in).
 * - Auto-refreshes after 5 minutes; locks after 5 wrong 6-digit codes until manually refreshed.
 */
@Composable
private fun TvDeviceLinkQrPanel(account: AccountUi) {
    val context = LocalContext.current
    val palette = LocalQuernPalette.current
    val link = account.deviceLink
    val tvDeviceName = remember {
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL.orEmpty().ifBlank { "Quern TV" }
    }

    DisposableEffect(Unit) {
        account.onStartDeviceLink(tvDeviceName)
        onDispose { account.onStopDeviceLink() }
    }

    var nowMs by remember(link.sessionId, link.expiresAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(link.sessionId, link.expiresAtMs, link.isActive, link.isLocked, link.isCompleted) {
        if (!link.isActive || link.isLocked || link.isCompleted || link.expiresAtMs <= 0L) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= link.expiresAtMs) break
            delay(1000L)
        }
        // Automatically rotate expired QR after 5 minutes so the QR on screen always works.
        account.onRefreshDeviceLink(tvDeviceName)
    }

    val isExpired = link.isExpired(nowMs)
    val remainingSec = link.remainingSeconds(nowMs)
    val countdown = String.format(Locale.US, "%02d:%02d", remainingSec / 60, remainingSec % 60)
    val qrUrl = remember(link.sessionId, link.publicKeyB64, link.isActive) { account.deviceLinkUrl() }
    val dimmed = link.isLocked || isExpired || !link.isActive

    val statusColor = when {
        link.isSigningIn -> LumaGreen
        link.isLocked || isExpired -> LumaRed
        else -> palette.focusRing
    }
    val statusText = when {
        link.isSigningIn -> qString(R.string.qr_link_signing_in)
        link.isLocked -> qString(R.string.qr_link_locked)
        isExpired -> qString(R.string.qr_link_expired)
        else -> qString(R.string.qr_link_waiting, countdown)
    }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: QR code (holds ONLY public key, session id, and device name)
            Box(contentAlignment = Alignment.Center) {
                if (qrUrl.isNotBlank()) {
                    QrCodeCard(
                        content = qrUrl,
                        size = 156.dp,
                        modifier = Modifier.alpha(if (dimmed) 0.25f else 1f)
                    )
                }
            }

            // Right: 6-digit confirmation code + instructions + live status + Refresh action
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, null, tint = palette.focusRing, modifier = Modifier.size(20.dp))
                        Text(
                            text = qString(R.string.qr_link_title),
                            color = LumaTextPrimary,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            softWrap = false,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // 6-digit confirmation code shown next to the QR (never embedded in the QR URL)
                Surface(
                    color = palette.surfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.2.dp, palette.focusRing.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = qString(R.string.qr_link_code_label).uppercase(),
                            color = LumaTextSecondary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = DeviceLinkCrypto.formatCode6ForDisplay(link.code6),
                            color = LumaTextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp
                        )
                    }
                }

                Text(
                    text = qString(R.string.qr_link_steps),
                    color = LumaTextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                )

                link.lastError?.let { err ->
                    Text(
                        text = err,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                var refreshFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (refreshFocused || link.isLocked || isExpired) palette.accent else palette.surfaceElevated,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        width = if (refreshFocused) 2.5.dp else 1.dp,
                        color = if (refreshFocused) palette.focusRing else palette.border
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .tvClickable(
                            onFocusChangedState = { refreshFocused = it },
                            onClick = { account.onRefreshDeviceLink(tvDeviceName) }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fg = if (refreshFocused || link.isLocked || isExpired) palette.onAccent else LumaTextPrimary
                        Icon(Icons.Default.Refresh, null, tint = fg, modifier = Modifier.size(14.dp))
                        Text(
                            text = qString(R.string.qr_link_refresh),
                            color = fg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
