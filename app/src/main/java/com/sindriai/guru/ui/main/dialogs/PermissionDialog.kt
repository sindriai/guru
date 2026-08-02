package com.sindriai.guru.ui.main.dialogs

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sindriai.guru.R

@Composable
fun PermissionDialog(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    // Only runtime permissions actually needed by this screen
    val requiredPermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)

            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    fun computeAllGranted(): Boolean =
        requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

    val prefs = remember {
        context.getSharedPreferences("permission_gate_prefs", Context.MODE_PRIVATE)
    }
    val askedKey = "has_requested_runtime_permissions"
    var hasRequestedOnce by remember { mutableStateOf(prefs.getBoolean(askedKey, false)) }

    var requestInFlight by remember { mutableStateOf(false) }
    var lastRequestHadDenial by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    fun refresh() {
        refreshTick++
    }

    fun computePermanentlyDeniedAfterResult(): Boolean {
        if (!hasRequestedOnce) return false
        if (!lastRequestHadDenial) return false

        return requiredPermissions.any { perm ->
            val granted =
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            !granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
        }
    }

    var allGranted by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick, requestInFlight, hasRequestedOnce, lastRequestHadDenial) {
        if (!requestInFlight) {
            allGranted = computeAllGranted()
            permanentlyDenied = computePermanentlyDeniedAfterResult()
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        requestInFlight = false

        if (!hasRequestedOnce) {
            hasRequestedOnce = true
            prefs.edit().putBoolean(askedKey, true).apply()
        }

        lastRequestHadDenial = requiredPermissions.any { result[it] == false }
        refresh()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        refresh()
    }

    if (allGranted) {
        content()
        return
    }

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    AlertDialog(
        onDismissRequest = { },
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Permissions required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Guru needs these permissions to work properly:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PermissionRow(
                    iconRes = R.drawable.ic_mic_on,
                    title = "Microphone",
                    description = "For voice input and conversations",
                    granted = isGranted(Manifest.permission.RECORD_AUDIO)
                )

                PermissionRow(
                    iconRes = R.drawable.ic_upload_camera,
                    title = "Camera",
                    description = "To capture images for questions and learning",
                    granted = isGranted(Manifest.permission.CAMERA)
                )

                if (Build.VERSION.SDK_INT >= 33) {
                    PermissionRow(
                        iconRes = R.drawable.ic_notifications,
                        title = "Notifications",
                        description = "To show download progress notifications",
                        granted = isGranted(Manifest.permission.POST_NOTIFICATIONS)
                    )
                }

                if (permanentlyDenied) {
                    Divider(modifier = Modifier.padding(top = 6.dp))
                    Text(
                        text = "You previously denied permissions. Please enable them from system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (permanentlyDenied) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        settingsLauncher.launch(intent)
                    } else {
                        if (!hasRequestedOnce) {
                            hasRequestedOnce = true
                            prefs.edit().putBoolean(askedKey, true).apply()
                        }
                        requestInFlight = true
                        permissionLauncher.launch(requiredPermissions)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (permanentlyDenied) "Open Settings" else "Grant permissions")
            }
        }
    )
}

@Composable
private fun PermissionRow(
    iconRes: Int,
    title: String,
    description: String,
    granted: Boolean,
    isInformational: Boolean = false
) {
    val containerColor =
        if (granted) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    val iconTint =
        if (granted) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = containerColor,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val label = when {
            isInformational -> "Included"
            granted -> "Allowed"
            else -> "Required"
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}