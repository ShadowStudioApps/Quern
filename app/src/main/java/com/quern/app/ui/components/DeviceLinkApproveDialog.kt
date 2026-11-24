package com.quern.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quern.app.R
import com.quern.app.data.sync.DeviceLinkClient
import com.quern.app.data.sync.DeviceLinkCrypto
import com.quern.app.data.sync.DeviceLinkSubmitResult
import com.quern.app.data.sync.PendingDeviceLink
import com.quern.app.data.sync.QuernAccountState
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaGreen
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Phone-side confirmation dialog opened from `quern://link?...` when scanning a TV sign-in QR.
 * Displays the TV's device name, requires the 6-digit confirmation code shown on the TV, seals
 * `{ email, authSecret, encKey }` via ECDH + HKDF-SHA256 + AES-256-GCM, and POSTs it to the TV.
 */
@Composable
fun DeviceLinkApproveDialog(
    request: PendingDeviceLink,
    accountState: QuernAccountState,
    canApprove: Boolean,
    httpClient: OkHttpClient,
    onApprove: suspend (PendingDeviceLink, String) -> DeviceLinkSubmitResult,
    onOpenAccountSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalQuernPalette.current
    val scope = rememberCoroutineScope()

    var tvName by remember(request.sessionId) { mutableStateOf(request.deviceName) }
    var code6 by remember(request.sessionId) { mutableStateOf("") }
    var busy by remember(request.sessionId) { mutableStateOf(false) }
    var succeeded by remember(request.sessionId) { mutableStateOf(false) }
    var statusError by remember(request.sessionId) { mutableStateOf<String?>(null) }
    var sessionInactive by remember(request.sessionId) { mutableStateOf(false) }

    val errLocked = qString(R.string.qr_link_approve_locked)
    val errExpired = qString(R.string.qr_link_approve_expired)
    val errUsed = qString(R.string.qr_link_approve_used)
    val errInactive = qString(R.string.qr_link_approve_inactive)
    val errNetwork = qString(R.string.qr_link_approve_network)

    LaunchedEffect(request.sessionId, accountState.isSignedIn, canApprove) {
        if (!accountState.isSignedIn || !canApprove) return@LaunchedEffect
        val info = DeviceLinkClient.fetchTvInfo(httpClient, request)
        if (info == null) {
            statusError = errNetwork
        } else {
            tvName = info.deviceName
            when {
                info.completed -> {
                    sessionInactive = true
                    statusError = errUsed
                }
                info.locked -> {
                    sessionInactive = true
                    statusError = errLocked
                }
                !info.active -> {
                    sessionInactive = true
                    statusError = if (info.expiresInSec <= 0L) errExpired else errInactive
                }
            }
        }
    }

    QuernAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = palette.surfaceElevated,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (succeeded) Icons.Default.CheckCircle else Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (succeeded) LumaGreen else palette.focusRing,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = qString(R.string.qr_link_approve_title, tvName),
                    color = LumaTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    succeeded -> {
                        Text(
                            text = qString(R.string.qr_link_approve_success),
                            color = LumaGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    !accountState.isSignedIn -> {
                        Text(
                            text = qString(R.string.qr_link_approve_not_signed_in),
                            color = LumaTextSecondary,
                            fontSize = 13.5.sp
                        )
                    }
                    !canApprove -> {
                        Text(
                            text = qString(R.string.qr_link_approve_resign_needed),
                            color = LumaTextSecondary,
                            fontSize = 13.5.sp
                        )
                    }
                    else -> {
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = qString(R.string.qr_link_approve_account, accountState.email),
                                color = LumaTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }

                        Text(
                            text = qString(R.string.qr_link_approve_code_hint),
                            color = LumaTextSecondary,
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = code6,
                            onValueChange = { raw ->
                                code6 = DeviceLinkCrypto.normalizeCode6(raw)
                                if (statusError != null && !sessionInactive) statusError = null
                            },
                            label = { Text(qString(R.string.qr_link_approve_code_label)) },
                            placeholder = { Text("000000") },
                            singleLine = true,
                            enabled = !busy && !sessionInactive,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 6.sp,
                                textAlign = TextAlign.Center
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.focusRing,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
                                focusedLabelColor = palette.focusRing,
                                unfocusedLabelColor = LumaTextSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                statusError?.let { err ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = err,
                        color = LumaRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            when {
                succeeded -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                    ) {
                        Text(qString(R.string.common_close), fontWeight = FontWeight.Bold)
                    }
                }
                !accountState.isSignedIn || !canApprove -> {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenAccountSettings()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                    ) {
                        Text(qString(R.string.qr_link_approve_open_settings), fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    val wrongCodeFmt = qString(R.string.qr_link_approve_wrong_code, 999)
                    Button(
                        enabled = !busy && !sessionInactive && code6.length == 6,
                        onClick = {
                            if (busy || sessionInactive || code6.length != 6) return@Button
                            busy = true
                            statusError = null
                            scope.launch {
                                val res = onApprove(request, code6)
                                busy = false
                                when (res) {
                                    is DeviceLinkSubmitResult.Success -> succeeded = true
                                    is DeviceLinkSubmitResult.WrongCode -> {
                                        code6 = ""
                                        if (res.locked) {
                                            sessionInactive = true
                                            statusError = errLocked
                                        } else {
                                            statusError = wrongCodeFmt.replace("999", res.attemptsLeft.toString())
                                        }
                                    }
                                    DeviceLinkSubmitResult.Locked -> {
                                        sessionInactive = true
                                        statusError = errLocked
                                    }
                                    DeviceLinkSubmitResult.Expired -> {
                                        sessionInactive = true
                                        statusError = errExpired
                                    }
                                    DeviceLinkSubmitResult.AlreadyUsed -> {
                                        sessionInactive = true
                                        statusError = errUsed
                                    }
                                    DeviceLinkSubmitResult.Inactive -> {
                                        sessionInactive = true
                                        statusError = errInactive
                                    }
                                    is DeviceLinkSubmitResult.Failed -> {
                                        statusError = if (res.detail == "network") errNetwork else res.detail
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                    ) {
                        Text(
                            text = if (busy) qString(R.string.qr_link_approve_sending) else qString(R.string.qr_link_approve_allow),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!succeeded) {
                TextButton(enabled = !busy, onClick = onDismiss) {
                    Text(qString(R.string.common_cancel), color = LumaTextSecondary)
                }
            }
        }
    )
}
