package com.example.wifiauditor

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.wifiauditor.ui.MainScreen
import com.example.wifiauditor.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7C3AED), secondary = Color(0xFFDC2626), error = Color(0xFFDC2626))) { var showSplash by remember { mutableStateOf(true) }; LaunchedEffect(Unit) { delay(1500); showSplash = false }; if (showSplash) { Box(Modifier.fillMaxSize().background(Color(0xFF0F0A1A)), contentAlignment = Alignment.Center) { Text("AIZO", color = Color(0xFF7C3AED), fontSize = 48.sp, fontWeight = FontWeight.Bold) } } else { MainScreen(vm, ::requestPerms) } } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus() // пользователь мог включить геолокацию/Wi-Fi в шторке
    }

    private fun requestPerms() {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        permLauncher.launch(p.toTypedArray())
    }
}
