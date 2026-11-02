package com.callbook.journal.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.callbook.journal.ui.DipAlerts
import com.callbook.journal.ui.components.Eyebrow
import com.callbook.journal.ui.components.Panel
import com.callbook.journal.ui.theme.Accent
import com.callbook.journal.ui.theme.Mist
import com.callbook.journal.ui.theme.Paper
import com.callbook.journal.ui.theme.setThemeDark
import com.callbook.journal.ui.theme.themeIsDark

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var backgroundOn by remember { mutableStateOf(DipAlerts.enabled(context)) }
    var phoneAwake by remember { mutableStateOf(DipAlerts.phoneKeepsAwake(context)) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            backgroundOn = DipAlerts.enabled(context)
            phoneAwake = DipAlerts.phoneKeepsAwake(context)
        }
    }
    val dark = themeIsDark()
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Settings",
            color = Paper,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
        )
        Text(
            text = "Appearance and alerts.",
            color = Mist,
            fontSize = 13.sp,
        )
        Eyebrow("Appearance", modifier = Modifier.padding(top = 16.dp))
        Panel {
            SettingRow(
                title = "Theme",
                value = if (dark) "Dark" else "Light",
                onClick = { setThemeDark(context, !dark) },
            )
            Text(
                text = if (dark) "Near-black page. Green and red stay the same." else "White page.",
                modifier = Modifier.padding(top = 4.dp),
                color = Mist,
                fontSize = 12.sp,
            )
        }
        Eyebrow("Alerts", modifier = Modifier.padding(top = 12.dp))
        Panel {
            SettingRow(
                title = "Background",
                value = if (backgroundOn) "On" else "Off",
                onClick = {
                    val next = !backgroundOn
                    DipAlerts.setEnabled(context, next)
                    backgroundOn = next
                    phoneAwake = DipAlerts.phoneKeepsAwake(context)
                    if (next && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            Text(
                text = if (backgroundOn) {
                    "On keeps the 9:15 check going after you leave."
                } else {
                    "Off stops the check when Callbook is closed."
                },
                modifier = Modifier.padding(top = 4.dp),
                color = Mist,
                fontSize = 12.sp,
            )
            if (phoneAwake) {
                Text(
                    text = "Phone battery is allowed. Tap to turn that off.",
                    modifier = Modifier
                        .clickable { DipAlerts.openPhoneBattery(context) }
                        .padding(top = 8.dp),
                    color = Accent,
                    fontSize = 13.sp,
                )
            } else if (backgroundOn) {
                Text(
                    text = "Tap to let the phone keep Callbook awake.",
                    modifier = Modifier
                        .clickable { DipAlerts.openPhoneBattery(context) }
                        .padding(top = 8.dp),
                    color = Accent,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Paper,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
        )
        Text(
            text = value,
            color = Accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}
