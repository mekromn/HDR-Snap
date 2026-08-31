package com.mekromn.hdrsnap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mekromn.hdrsnap.capture.HdrSnapBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HdrSnapApp() }
    }

    @Composable
    private fun HdrSnapApp() {
        val prefs = remember { HdrSnapPrefs(this) }
        var serviceConnected by remember { mutableStateOf(HdrSnapBridge.isConnected) }
        var photoPermission by remember {
            mutableStateOf(checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED)
        }
        var trueHdrConversion by remember { mutableStateOf(prefs.convertTrueHdrToJpegR) }
        var sdrUpconvert by remember { mutableStateOf(prefs.sdrUpconversionEnabled) }
        var lastStatus by remember { mutableStateOf(prefs.lastStatus) }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            photoPermission = it
        }

        val colors = darkColorScheme(
            primary = Color(0xFFB7C7FF),
            secondary = Color(0xFF8EE7D1),
            surface = Color(0xFF11151D),
            background = Color(0xFF080A0F)
        )

        MaterialTheme(colorScheme = colors) {
            val background = Brush.verticalGradient(
                listOf(Color(0xFF0B1020), Color(0xFF080A0F), Color(0xFF0D0B16))
            )
            Scaffold(containerColor = Color.Transparent) { insets ->
                Box(Modifier.fillMaxSize().background(background).padding(insets)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Text("HDR Snap", fontSize = 38.sp, fontWeight = FontWeight.Black)
                        Text(
                            "True HDR screenshots first. Native Android gainmaps are preserved, not reconstructed.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                        )

                        StatusCard(
                            title = if (Build.VERSION.SDK_INT >= 36) "Android 16 HDR path available" else "Android 16 required for native HDR screenshots",
                            good = Build.VERSION.SDK_INT >= 36,
                            detail = "System screenshot → gainmapped PNG → verified JPEG/R Ultra HDR companion"
                        )

                        StatusCard(
                            title = if (serviceConnected) "Capture service enabled" else "Capture service needs enabling",
                            good = serviceConnected,
                            detail = "Uses Android's system screenshot action rather than MediaProjection."
                        )

                        StatusCard(
                            title = if (photoPermission) "Screenshot access granted" else "Photo access required",
                            good = photoPermission,
                            detail = "Needed to detect the system-created screenshot and inspect its embedded gainmap."
                        )

                        Button(
                            onClick = {
                                serviceConnected = HdrSnapBridge.isConnected
                                if (serviceConnected) {
                                    HdrSnapBridge.requestSystemScreenshot()
                                } else {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (serviceConnected) "Capture HDR screenshot" else "Enable capture service")
                        }

                        if (!photoPermission) {
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant screenshot access")
                            }
                        }

                        SettingCard(
                            title = "Ultra HDR JPEG/R companion",
                            detail = "For a genuine Android HDR screenshot, encode the existing gainmap at JPEG quality 100 and verify it after saving.",
                            checked = trueHdrConversion,
                            onChecked = {
                                trueHdrConversion = it
                                prefs.convertTrueHdrToJpegR = it
                            }
                        )

                        SettingCard(
                            title = "Convert SDR screenshots too",
                            detail = "OFF by default. When enabled, HDR Snap synthesizes a gainmap and writes SDR_UPCONVERTED / NativeHDR=false into EXIF and the filename.",
                            checked = sdrUpconvert,
                            onChecked = {
                                sdrUpconvert = it
                                prefs.sdrUpconversionEnabled = it
                            }
                        )

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
                            modifier = Modifier.fillMaxWidth().animateContentSize()
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Last result", fontWeight = FontWeight.Bold)
                                Text(lastStatus, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = {
                                        HdrSnapBridge.processLatestScreenshot()
                                        lastStatus = prefs.lastStatus
                                    }) { Text("Process latest") }
                                    Button(onClick = {
                                        serviceConnected = HdrSnapBridge.isConnected
                                        photoPermission = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                                        lastStatus = prefs.lastStatus
                                    }) { Text("Refresh") }
                                }
                            }
                        }

                        Text(
                            "Archival rule: the Android 16 gainmapped PNG is never deleted by HDR Snap. JPEG/R is a compatibility companion, not a replacement master.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(title: String, good: Boolean, detail: String) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (good) Color(0xFF13241F) else Color(0xFF24191B)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
            }
        }
    }

    @Composable
    private fun SettingCard(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(detail, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Switch(checked = checked, onCheckedChange = onChecked)
            }
        }
    }
}
