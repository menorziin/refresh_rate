package com.marble.refreshrate

import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            MaterialTheme(colorScheme = scheme) { Surface { HzScreen() } }
        }
    }
}

@Composable
private fun HzScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    val rates = remember { RefreshRate.supported(ctx) }

    var current by remember { mutableIntStateOf(RefreshRate.current(ctx)) }
    var selected by remember { mutableIntStateOf(prefs.lastHz) }
    var hasRoot by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var applyOnBoot by remember { mutableStateOf(prefs.applyOnBoot) }
    var forceSf by remember { mutableStateOf(prefs.forceSf) }

    LaunchedEffect(Unit) { hasRoot = withContext(Dispatchers.IO) { Root.isAvailable() } }

    // Atualiza a taxa exibida em tempo real quando o painel muda de modo.
    DisposableEffect(Unit) {
        val dm = ctx.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(id: Int) { current = RefreshRate.current(ctx) }
            override fun onDisplayAdded(id: Int) {}
            override fun onDisplayRemoved(id: Int) {}
        }
        dm.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { dm.unregisterDisplayListener(listener) }
    }

    fun choose(hz: Int) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { RefreshRate.apply(ctx, hz, forceSf) }
            busy = false
            result.onSuccess {
                selected = hz; prefs.lastHz = hz; message = null
            }.onFailure { message = "Falhou: ${it.message}" }
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Hz Switcher", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Agora", style = MaterialTheme.typography.labelLarge)
                Text("$current Hz", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text(
                    when (hasRoot) {
                        null -> "Verificando root…"
                        true -> "Root OK"
                        false -> "Sem root — conceda acesso no Magisk/KernelSU"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasRoot == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val options = rates + HZ_DYNAMIC
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { hz ->
                val label = if (hz == HZ_DYNAMIC) "Dinâmico (${rates.first()}–${rates.last()} Hz)" else "$hz Hz"
                val isSel = hz == selected
                if (isSel) {
                    Button({ choose(hz) }, Modifier.fillMaxWidth().height(56.dp), enabled = hasRoot == true && !busy) { Text(label) }
                } else {
                    OutlinedButton({ choose(hz) }, Modifier.fillMaxWidth().height(56.dp), enabled = hasRoot == true && !busy) { Text(label) }
                }
            }
        }

        SettingRow("Reaplicar ao reiniciar", applyOnBoot) { applyOnBoot = it; prefs.applyOnBoot = it }
        SettingRow("Forçar via SurfaceFlinger (experimental)", forceSf) { forceSf = it; prefs.forceSf = it }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.weight(1f))
        Text(
            "Dica: adicione o bloco \"Taxa de atualização\" nas Configurações Rápidas para trocar sem abrir o app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
