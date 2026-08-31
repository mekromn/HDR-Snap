package com.mekromn.hdrsnap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    private var resumeGeneration by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HdrSnapApp() }
    }

    override fun onResume() {
        super.onResume()
        resumeGeneration++
    }

    private fun openHdrSnapAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun openMediaManagementAccess() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    @Composable
    private fun HdrSnapApp() {
        val prefs = remember { HdrSnapPrefs(this) }
        var serviceConnected by remember { mutableStateOf(HdrSnapBridge.isConnected) }
        var photoPermission by remember {
            mutableStateOf(
                checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
            )
        }
        var mediaManagement by remember { mutableStateOf(MediaStore.canManageMedia(this)) }
        var autoProcess by remember { mutableStateOf(prefs.autoProcessScreenshots) }
        var trueHdrConversion by remember { mutableStateOf(prefs.convertTrueHdrToJpegR) }
        var sdrUpconvert by remember { mutableStateOf(prefs.sdrUpconversionEnabled) }
        var deleteOriginal by remember { mutableStateOf(prefs.deleteOriginalAfterVerify) }
        var lastStatus by remember { mutableStateOf(prefs.lastStatus) }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> photoPermission = granted }

        LaunchedEffect(resumeGeneration) {
            serviceConnected = HdrSnapBridge.isConnected
            photoPermission = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
            mediaManagement = MediaStore.canManageMedia(this@MainActivity)
            lastStatus = prefs.lastStatus
        }

        val colors = darkColorScheme(
            primary = Color(0xFF8AB4F8),
            secondary = Color(0xFF72E6D1),
            surface = Color(0xFF11151D),
            background = Color(0xFF05070B)
        )

        MaterialTheme(colorScheme = colors) {
            val background = Brush.verticalGradient(
                listOf(Color(0xFF071426), Color(0xFF05070B), Color(0xFF120B16))
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
                            "Automatic gainmapped screenshots. True HDR is preserved; SDR-derived HDR is explicitly labeled.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                        )

                        StatusCard(
                            title = if (Build.VERSION.SDK_INT >= 36) {
                                "Android 16 HDR path available"
                            } else {
                                "Android 16 required for native HDR screenshots"
                            },
                            good = Build.VERSION.SDK_INT >= 36,
                            detail = "System screenshot → gainmap inspection → verified Ultra HDR replacement"
                        )

                        StatusCard(
                            title = if (serviceConnected) {
                                "Background screenshot watcher enabled"
                            } else {
                                "Background watcher needs enabling"
                            },
                            good = serviceConnected,
                            detail = "Once enabled, normal Power + Volume Down screenshots are processed automatically."
                        )

                        if (!serviceConnected) {
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF282036)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("Sideloaded APK setup", fontWeight = FontWeight.Bold)
                                    Text(
                                        "If Accessibility says ‘Controlled by Restricted Setting’, open HDR Snap app info, tap ⋮, choose Allow restricted settings, then enable HDR Snap in Accessibility.",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                        fontSize = 13.sp
                                    )
                                    Button(
                                        onClick = { openHdrSnapAppInfo() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Open HDR Snap app info") }
                                    Button(
                                        onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Open Accessibility") }
                                }
                            }
                        }

                        StatusCard(
                            title = if (photoPermission) {
                                "Screenshot access granted"
                            } else {
                                "Photo access required"
                            },
                            good = photoPermission,
                            detail = "Needed to inspect the system screenshot and its embedded gainmap."
                        )

                        if (!photoPermission) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Grant screenshot access") }
                        }

                        if (deleteOriginal) {
                            StatusCard(
                                title = if (mediaManagement) {
                                    "Automatic replacement access granted"
                                } else {
                                    "Media management access required"
                                },
                                good = mediaManagement,
                                detail = if (mediaManagement) {
                                    "Verified replacements can remove their superseded originals without per-screenshot prompts."
                                } else {
                                    "Without this one-time special access, HDR Snap will create the verified replacement but safely keep the original."
                                }
                            )
                            if (!mediaManagement) {
                                Button(
                                    onClick = { openMediaManagementAccess() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Grant automatic replacement access") }
                            }
                        }

                        SettingCard(
                            title = "Process every screenshot automatically",
                            detail = "Default ON. The watcher waits for Pixel Studio / Markup editing to finish before touching the file.",
                            checked = autoProcess,
                            onChecked = {
                                autoProcess = it
                                prefs.autoProcessScreenshots = it
                            }
                        )

                        SettingCard(
                            title = "Preserve native HDR as Ultra HDR JPEG/R",
                            detail = "Uses the existing Android gainmap, quality 100, then reopens the output to verify the gainmap survived.",
                            checked = trueHdrConversion,
                            onChecked = {
                                trueHdrConversion = it
                                prefs.convertTrueHdrToJpegR = it
                            }
                        )

                        SettingCard(
                            title = "Convert SDR screenshots too",
                            detail = "Default ON. SDR-derived files are marked SDR_UPCONVERTED / NativeHDR=false in EXIF and in the filename.",
                            checked = sdrUpconvert,
                            onChecked = {
                                sdrUpconvert = it
                                prefs.sdrUpconversionEnabled = it
                            }
                        )

                        SettingCard(
                            title = "Delete original after verified replacement",
                            detail = "Default ON. Deletion is transactional: no source is removed unless the final output reopens with an embedded gainmap.",
                            checked = deleteOriginal,
                            onChecked = {
                                deleteOriginal = it
                                prefs.deleteOriginalAfterVerify = it
                                mediaManagement = MediaStore.canManageMedia(this@MainActivity)
                            }
                        )

                        Button(
                            onClick = {
                                serviceConnected = HdrSnapBridge.isConnected
                                if (serviceConnected) {
                                    HdrSnapBridge.requestSystemScreenshotDelayed(1_000L)
                                    prefs.lastStatus = "Capture armed — returning to previous app, then capturing in 1 second."
                                    lastStatus = prefs.lastStatus
                                    moveTaskToBack(true)
                                } else {
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (serviceConnected) "Capture previous app in 1 second"
                                else "Enable background watcher"
                            )
                        }

                        Text(
                            "Normal use needs no HDR Snap button: take screenshots normally. Edited screenshots are held while Pixel Studio/Markup is open and processed after the editor publishes its final file.",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                            fontSize = 13.sp
                        )

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                            ),
                            modifier = Modifier.fillMaxWidth().animateContentSize()
                        ) {
                            Column(
                                Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Last result", fontWeight = FontWeight.Bold)
                                Text(
                                    lastStatus,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = {
                                        HdrSnapBridge.processLatestScreenshot()
                                        lastStatus = prefs.lastStatus
                                    }) { Text("Process latest") }
                                    Button(onClick = {
                                        serviceConnected = HdrSnapBridge.isConnected
                                        photoPermission = checkSelfPermission(
                                            Manifest.permission.READ_MEDIA_IMAGES
                                        ) == PackageManager.PERMISSION_GRANTED
                                        mediaManagement = MediaStore.canManageMedia(this@MainActivity)
                                        lastStatus = prefs.lastStatus
                                    }) { Text("Refresh") }
                                }
                            }
                        }

                        Text(
                            "Output folder: Pictures/Screenshots. Safety rule: verification first, deletion second — never the reverse.",
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
                containerColor = if (good) Color(0xFF10251F) else Color(0xFF28171B)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
            }
        }
    }

    @Composable
    private fun SettingCard(
        title: String,
        detail: String,
        checked: Boolean,
        onChecked: (Boolean) -> Unit
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(
                        detail,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }
                Switch(checked = checked, onCheckedChange = onChecked)
            }
        }
    }
}
