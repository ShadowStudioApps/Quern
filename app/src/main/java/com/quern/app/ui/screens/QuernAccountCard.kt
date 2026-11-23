package com.quern.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.data.sync.AccountCrypto
import com.quern.app.data.sync.AccountError
import com.quern.app.data.sync.AccountUiMessage
import com.quern.app.data.sync.QuernAccountState
import com.quern.app.data.sync.SyncStatus
import com.quern.app.ui.theme.LumaRed
import android.text.format.DateUtils
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString

/** Everything the account card needs, bundled so it can pass through the settings layers. */
class AccountUi(
    val state: QuernAccountState,
    val busy: Boolean,
    val message: AccountUiMessage?,
    val onSignIn: (email: String, password: String) -> Unit,
    val onSignUp: (email: String, password: String) -> Unit,
    val onResend: (email: String) -> Unit,
    val onSignOut: () -> Unit,
    val onDelete: () -> Unit,
    val sync: SyncStatus = SyncStatus(),
    val onSyncNow: () -> Unit = {}
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

/** Quern account on Shadow Studio's server: sign up / sign in / sign out / delete. */
@Composable
internal fun QuernAccountCard(account: AccountUi) {
    val palette = LocalQuernPalette.current
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectablePillOption(label = qString(R.string.qa_sync_now), selected = true) {
                    if (!account.sync.running) account.onSyncNow()
                }
                SelectablePillOption(label = qString(R.string.qa_sign_out), selected = false) {
                    if (!account.busy) account.onSignOut()
                }
                SelectablePillOption(label = qString(R.string.qa_delete), selected = false) {
                    if (!account.busy) confirmDelete = true
                }
            }
        } else {
            Text(qString(R.string.qa_desc_signed_out), color = LumaTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(qString(R.string.qa_privacy_note), color = LumaTextSecondary, fontSize = 12.sp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Spacer(Modifier.height(10.dp))
            HelpTextLine(qString(R.string.qa_phone_hint))
        }

        val message = account.message
        when {
            account.busy -> {
                Spacer(Modifier.height(10.dp))
                Text(qString(R.string.qa_working), color = LumaTextSecondary, fontSize = 13.sp)
            }
            message != null -> {
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
