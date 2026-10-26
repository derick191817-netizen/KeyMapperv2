package com.example.keymapper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.keymapper.data.AppSettings
import com.example.keymapper.service.MappingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        onRequestOverlay = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                            }
                        },
                        onStartService = { startForegroundService(Intent(this, MappingService::class.java)) },
                        onStopService = { stopService(Intent(this, MappingService::class.java)) }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(onRequestOverlay: () -> Unit, onStartService: () -> Unit, onStopService: () -> Unit) {
    var settings by remember { mutableStateOf(AppSettings()) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("KeyMapper (Windows Mode)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onRequestOverlay) { Text("1. Overlay") }
            Button(onClick = onStartService, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Iniciar") }
            Button(onClick = onStopService, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Parar") }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("Configurações de Ponteiro (Windows)", style = MaterialTheme.typography.titleMedium)
        
        Text("Velocidade do Ponteiro: ${settings.pointerSpeed.toInt()}")
        Slider(
            value = settings.pointerSpeed,
            onValueChange = { settings = settings.copy(pointerSpeed = it) },
            valueRange = 1f..20f
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Melhorar precisão (Aceleração)")
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = settings.enhancePrecision,
                onCheckedChange = { settings = settings.copy(enhancePrecision = it) }
            )
        }
        
        Text("Sensibilidade X: ${String.format("%.2f", settings.sensX)}")
        Slider(
            value = settings.sensX,
            onValueChange = { settings = settings.copy(sensX = it) },
            valueRange = 0.1f..3.0f
        )
        
        Text("Sensibilidade Y: ${String.format("%.2f", settings.sensY)}")
        Slider(
            value = settings.sensY,
            onValueChange = { settings = settings.copy(sensY = it) },
            valueRange = 0.1f..3.0f
        )
    }
}
