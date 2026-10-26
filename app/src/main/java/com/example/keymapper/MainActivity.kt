package com.example.keymapper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                        onStartService = {
                            startForegroundService(Intent(this, MappingService::class.java))
                        },
                        onStopService = {
                            stopService(Intent(this, MappingService::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(onRequestOverlay: () -> Unit, onStartService: () -> Unit, onStopService: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("KeyMapper", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(onClick = onRequestOverlay, modifier = Modifier.fillMaxWidth()) {
            Text("1. Conceder Permissão de Sobreposição")
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onStartService, modifier = Modifier.fillMaxWidth()) {
            Text("2. Iniciar Mapeamento (Overlay)")
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onStopService, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
            Text("3. Parar Mapeamento")
        }
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Atalho de Edição: Segure a tecla Voltar", style = MaterialTheme.typography.bodyMedium)
    }
}
