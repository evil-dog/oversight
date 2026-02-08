package us.bergnet.oversight.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import us.bergnet.oversight.data.store.OverlayStateStore
import us.bergnet.oversight.service.OverlayService
import us.bergnet.oversight.util.NetworkUtils

class SetupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request overlay permission
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            startOverlayService()
        }

        requestBatteryOptimizationExemption()

        setContent {
            SetupScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                @Suppress("BatteryLife")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun startOverlayService() {
        OverlayService.start(this)
    }

    @Composable
    private fun SetupScreen() {
        val isRunning by OverlayStateStore.isServiceRunning.collectAsState()
        val ip = remember { NetworkUtils.getDeviceIpAddress() ?: "Unknown" }
        val port = OverlayStateStore.getRemotePort()
        var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(this)) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "OverSight",
                    color = Color(0xFF6C63FF),
                    fontSize = 36.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isRunning) "Service: Running" else "Service: Stopped",
                    color = if (isRunning) Color(0xFF4CAF50) else Color(0xFFFF5252),
                    fontSize = 18.sp
                )

                Text(
                    text = "IP: $ip",
                    color = Color.White,
                    fontSize = 16.sp
                )

                Text(
                    text = "Port: $port",
                    color = Color.White,
                    fontSize = 16.sp
                )

                Text(
                    text = "Overlay Permission: ${if (hasOverlayPermission) "Granted" else "Required"}",
                    color = if (hasOverlayPermission) Color(0xFF4CAF50) else Color(0xFFFF5252),
                    fontSize = 14.sp
                )

                if (!hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            requestOverlayPermission()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                    ) {
                        Text("Grant Overlay Permission")
                    }
                }

                if (!isRunning && hasOverlayPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { startOverlayService() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF))
                    ) {
                        Text("Start Service")
                    }
                }
            }
        }
    }
}
