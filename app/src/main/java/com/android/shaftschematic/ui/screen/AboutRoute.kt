package com.android.shaftschematic.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.shaftschematic.ui.viewmodel.ShaftViewModel
import com.android.shaftschematic.util.Achievements
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutRoute(
    vm: ShaftViewModel,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val devOptionsEnabled by vm.devOptionsEnabled.collectAsState()

    val versionText = remember { appVersionText(ctx) }
    val packageName = remember { ctx.packageName }
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    var aboutTapCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(devOptionsEnabled) {
        if (devOptionsEnabled) aboutTapCount = 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ShaftSchematic", style = MaterialTheme.typography.titleLarge)
            Text("PlusOrMinusTwo Designs", style = MaterialTheme.typography.bodyMedium)
            Text("© $currentYear PlusOrMinusTwo Designs", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

            ListItem(
                headlineContent = { Text("App Version") },
                supportingContent = { Text(versionText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!devOptionsEnabled) {
                            val nextCount = aboutTapCount + 1
                            aboutTapCount = nextCount
                            val remaining = 7 - nextCount

                            when {
                                remaining == 0 -> {
                                    vm.setDevOptionsEnabled(true)
                                    vm.unlockAchievement(Achievements.Id.DEVOPS_UNLOCKED)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar("BOOM DevOps Unlocked")
                                    }
                                }

                                remaining in 1..3 -> {
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            if (remaining == 1) "1 more tap to unlock Developer Options"
                                            else "$remaining more taps to unlock Developer Options"
                                        )
                                    }
                                }
                            }
                        }
                    }
            )

            ListItem(
                headlineContent = { Text("Package") },
                supportingContent = { Text(packageName) },
            )

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(0.dp))
                Text(
                    "All model geometry is stored in millimeters (mm).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun appVersionText(context: Context): String {
    return try {
        val pm = context.packageManager
        val pkg = context.packageName
        val pi = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        val name = pi.versionName ?: "0"
        val code = if (Build.VERSION.SDK_INT >= 28) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
        "$name ($code)"
    } catch (_: Throwable) {
        "0"
    }
}
