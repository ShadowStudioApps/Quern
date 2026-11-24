package com.quern.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.quern.app.R
import com.quern.app.data.companion.TvLinkNsdDiscovery
import com.quern.app.data.sync.DeviceLinkClient
import com.quern.app.data.sync.DeviceLinkSubmitResult
import com.quern.app.data.sync.PendingDeviceLink
import com.quern.app.data.sync.QuernAccountState
import com.quern.app.ui.theme.LocalQuernPalette
import com.quern.app.ui.theme.LumaRed
import com.quern.app.ui.theme.LumaTextPrimary
import com.quern.app.ui.theme.LumaTextSecondary
import com.quern.app.ui.theme.qString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Phone-only "Link a TV" flow inside Settings → Accounts & Sync:
 * 1. Discovers TVs advertising `_quern-link._tcp` over LAN NSD while their sign-in QR is open.
 * 2. Offers an in-app CameraX + ZXing "Scan QR" option on devices with a camera (`FEATURE_CAMERA_ANY`),
 *    asking for `CAMERA` permission only when tapped and keeping discovery + manual entry working if denied.
 * 3. After ~8 s without finding a TV (or on tap), shows help text and a manual `http://<ip>:8787` field.
 * 4. Hands off any selected/scanned/resolved TV to [DeviceLinkApproveDialog] (6-digit code + sealed ECDH envelope).
 */
@Composable
fun PhoneLinkTvDialog(
    accountState: QuernAccountState,
    canApprove: Boolean,
    httpClient: OkHttpClient,
    onApprove: suspend (PendingDeviceLink, String) -> DeviceLinkSubmitResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    if (isTvHardware(context)) return

    val palette = LocalQuernPalette.current
    val scope = rememberCoroutineScope()
    val hasCameraHardware = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    var selectedTv by remember { mutableStateOf<PendingDeviceLink?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var showManualAddress by remember { mutableStateOf(false) }
    var timedOutWithNoTv by remember { mutableStateOf(false) }
    var manualAddressInput by remember { mutableStateOf("") }
    var resolvingManual by remember { mutableStateOf(false) }
    var manualError by remember { mutableStateOf<String?>(null) }

    val errInvalidAddr = qString(R.string.link_tv_err_invalid_addr)
    val errInactive = qString(R.string.qr_link_approve_inactive)
    val errNetwork = qString(R.string.qr_link_approve_network)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            showScanner = true
        } else {
            cameraPermissionDenied = true
        }
    }

    // Delegate straight to the 6-digit code confirmation dialog once a TV is chosen/scanned/resolved.
    selectedTv?.let { target ->
        DeviceLinkApproveDialog(
            request = target,
            accountState = accountState,
            canApprove = canApprove,
            httpClient = httpClient,
            onApprove = onApprove,
            onOpenAccountSettings = onDismiss,
            onDismiss = onDismiss
        )
        return
    }

    // Isolated scanner branch: PhoneQrScannerHost (and CameraX) only loads when user taps Scan QR on a camera phone.
    if (showScanner && hasCameraHardware) {
        PhoneQrScannerHost.Dialog(
            onScanned = { link ->
                showScanner = false
                selectedTv = link
            },
            onDismiss = { showScanner = false }
        )
        return
    }

    val discovery = remember(context, httpClient) { TvLinkNsdDiscovery(context, httpClient) }
    val discoveredTvs by discovery.discoveredTvs.collectAsState()

    DisposableEffect(discovery, canApprove) {
        if (canApprove) {
            discovery.start(scope)
        }
        onDispose { discovery.stop() }
    }

    LaunchedEffect(discoveredTvs.isEmpty(), canApprove) {
        if (!canApprove) return@LaunchedEffect
        if (discoveredTvs.isEmpty()) {
            delay(8_000L)
            if (discoveredTvs.isEmpty()) {
                timedOutWithNoTv = true
                showManualAddress = true
            }
        } else {
            timedOutWithNoTv = false
        }
    }

    val connectManual: () -> Unit = {
        if (!resolvingManual && manualAddressInput.isNotBlank()) {
            resolvingManual = true
            manualError = null
            scope.launch {
                val res = DeviceLinkClient.resolveTvAddress(httpClient, manualAddressInput)
                resolvingManual = false
                res.fold(
                    onSuccess = { link -> selectedTv = link },
                    onFailure = { err ->
                        manualError = when (err.message) {
                            "invalid_address" -> errInvalidAddr
                            "inactive" -> errInactive
                            else -> errNetwork
                        }
                    }
                )
            }
        }
    }

    QuernAlertDialog(
        onDismissRequest = { if (!resolvingManual) onDismiss() },
        containerColor = palette.surfaceElevated,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = palette.focusRing,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = qString(R.string.link_tv_title),
                    color = LumaTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            if (!canApprove) {
                Text(
                    text = qString(R.string.qr_link_approve_resign_needed),
                    color = LumaTextSecondary,
                    fontSize = 13.5.sp
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = qString(R.string.link_tv_subtitle),
                        color = LumaTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (hasCameraHardware) {
                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    cameraPermissionDenied = false
                                    showScanner = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.2.dp, palette.focusRing.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = palette.focusRing,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = qString(R.string.link_tv_scan_qr_btn),
                                color = LumaTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }

                    if (cameraPermissionDenied) {
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, LumaRed.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = qString(R.string.link_tv_camera_denied),
                                color = LumaTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (discoveredTvs.isNotEmpty()) {
                        Text(
                            text = qString(R.string.link_tv_found_header),
                            color = LumaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            discoveredTvs.forEach { tv ->
                                Surface(
                                    color = palette.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, palette.focusRing.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedTv = tv }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = palette.focusRing,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = tv.deviceName,
                                                    color = LumaTextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = tv.tvOrigin,
                                                    color = LumaTextSecondary,
                                                    fontSize = 11.5.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = LumaTextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!timedOutWithNoTv) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                color = palette.focusRing,
                                strokeWidth = 2.2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = qString(R.string.link_tv_searching),
                                color = LumaTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (timedOutWithNoTv && discoveredTvs.isEmpty()) {
                        Text(
                            text = qString(R.string.link_tv_not_found_help),
                            color = LumaTextSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    } else if (!showManualAddress) {
                        TextButton(
                            onClick = { showManualAddress = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = qString(R.string.link_tv_manual_toggle),
                                color = palette.focusRing,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showManualAddress || timedOutWithNoTv) {
                        OutlinedTextField(
                            value = manualAddressInput,
                            onValueChange = {
                                manualAddressInput = it
                                if (manualError != null) manualError = null
                            },
                            label = { Text(qString(R.string.link_tv_address_label)) },
                            placeholder = { Text(qString(R.string.link_tv_address_placeholder)) },
                            singleLine = true,
                            enabled = !resolvingManual,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = { connectManual() }),
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

                        Button(
                            onClick = connectManual,
                            enabled = !resolvingManual && manualAddressInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.accent,
                                contentColor = palette.onAccent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = if (resolvingManual) {
                                    qString(R.string.link_tv_connecting)
                                } else {
                                    qString(R.string.link_tv_connect_btn)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        manualError?.let { err ->
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = err,
                                color = LumaRed,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(enabled = !resolvingManual, onClick = onDismiss) {
                Text(qString(R.string.common_cancel), color = LumaTextSecondary)
            }
        }
    )
}
