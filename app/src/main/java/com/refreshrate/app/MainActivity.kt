package com.refreshrate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val presets = remember { RefreshRate.presets(ctx) }

    var current by remember { mutableIntStateOf(RefreshRate.current(ctx)) }
    var selected by remember { mutableStateOf(prefs.last) }
    var access by remember { mutableStateOf<Access?>(null) }
    val hasAccess = access == Access.ROOT || access == Access.SHIZUKU
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var applyOnBoot by remember { mutableStateOf(prefs.applyOnBoot) }
    var forceSf by remember { mutableStateOf(prefs.forceSf) }
    var overlayOn by remember { mutableStateOf(prefs.overlayEnabled) }

    // Ao voltar (ex.: da tela de permissão ou depois de ocultar pela notificação),
    // confirma se a sobreposição pode e deve estar ligada.
    LifecycleResumeEffect(Unit) {
        if (prefs.overlayEnabled && !Settings.canDrawOverlays(ctx)) prefs.overlayEnabled = false
        overlayOn = prefs.overlayEnabled
        OverlayService.sync(ctx)
        onPauseOrDispose { }
    }

    fun setOverlay(on: Boolean) {
        prefs.overlayEnabled = on
        if (on && !Settings.canDrawOverlays(ctx)) {
            // Pede a permissão; o efeito acima liga a sobreposição na volta, se concedida.
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
            )
            return
        }
        overlayOn = on
        OverlayService.sync(ctx)
    }

    fun recheckAccess() {
        scope.launch { access = withContext(Dispatchers.IO) { Shell.detect(ctx) } }
    }

    // Reavalia ao voltar para o app (ex.: depois de iniciar o Shizuku). Se o acesso
    // já está ok, não repete, para não abrir o pedido de root a cada retorno.
    LifecycleResumeEffect(hasAccess) {
        if (!hasAccess) recheckAccess()
        onPauseOrDispose { }
    }

    // Resposta ao pedido de permissão do Shizuku.
    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> recheckAccess() }
        Shizuku.addRequestPermissionResultListener(listener)
        onDispose { Shizuku.removeRequestPermissionResultListener(listener) }
    }

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

    // Acompanha a economia de energia, que limita a taxa por conta própria.
    val pm = remember { ctx.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { powerSave = pm.isPowerSaveMode }
        }
        ContextCompat.registerReceiver(
            ctx, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    // Aviso quando a taxa real difere da escolhida. Espera o painel assentar
    // antes de avaliar, para não piscar durante uma troca.
    var warning by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selected, current, powerSave) {
        warning = null
        delay(1500)
        val sel = selected ?: return@LaunchedEffect
        warning = when {
            powerSave && current < sel.peak -> ctx.getString(R.string.warning_power_save, current)
            !sel.isDynamic && current != sel.peak -> ctx.getString(R.string.warning_not_applied, sel.label)
            else -> null
        }
    }

    fun choose(preset: Preset) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { RefreshRate.apply(ctx, preset, forceSf) }
            busy = false
            result.onSuccess { selected = preset; message = null }
                .onFailure { message = ctx.getString(R.string.error_failed, it.message) }
        }
    }

    @Composable
    fun PresetButton(preset: Preset, modifier: Modifier) {
        val enabled = hasAccess && !busy
        val content: @Composable RowScope.() -> Unit = { Text(preset.label, maxLines = 1) }
        if (preset == selected) Button({ choose(preset) }, modifier.height(56.dp), enabled = enabled, content = content)
        else OutlinedButton({ choose(preset) }, modifier.height(56.dp), enabled = enabled, content = content)
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.now), style = MaterialTheme.typography.labelLarge)
                Text("$current Hz", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                selected?.takeIf { it.isDynamic }?.let {
                    Text(stringResource(R.string.dynamic_mode, it.label), style = MaterialTheme.typography.bodyMedium)
                }
                warning?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                    )
                }
                AccessStatus(access)
            }
        }

        Text(stringResource(R.string.section_fixed), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.filterNot { it.isDynamic }.forEach { PresetButton(it, Modifier.weight(1f)) }
        }

        Text(stringResource(R.string.section_dynamic), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.filter { it.isDynamic }.forEach { PresetButton(it, Modifier.weight(1f)) }
        }

        SettingRow(stringResource(R.string.apply_on_boot), applyOnBoot) { applyOnBoot = it; prefs.applyOnBoot = it }
        SettingRow(stringResource(R.string.force_surfaceflinger), forceSf) { forceSf = it; prefs.forceSf = it }
        SettingRow(stringResource(R.string.overlay_toggle), overlayOn) { setOverlay(it) }

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.tip_tile),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccessStatus(access: Access?) {
    val ctx = LocalContext.current
    val ok = access == Access.ROOT || access == Access.SHIZUKU
    Text(
        stringResource(
            when (access) {
                null -> R.string.access_checking
                Access.ROOT -> R.string.access_root
                Access.SHIZUKU -> R.string.access_shizuku
                Access.SHIZUKU_NEEDS_PERMISSION -> R.string.access_shizuku_permission
                Access.SHIZUKU_NOT_RUNNING -> R.string.access_shizuku_not_running
                Access.NONE -> R.string.access_none
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = if (ok || access == null) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.error,
    )
    when (access) {
        Access.SHIZUKU_NEEDS_PERMISSION -> TextButton({ runCatching { Shizuku.requestPermission(1) } }) {
            Text(stringResource(R.string.action_grant))
        }
        Access.SHIZUKU_NOT_RUNNING -> TextButton({
            ctx.packageManager.getLaunchIntentForPackage(Shell.SHIZUKU_PACKAGE)?.let { ctx.startActivity(it) }
        }) { Text(stringResource(R.string.action_open_shizuku)) }
        Access.NONE -> TextButton({
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Shell.SHIZUKU_SETUP_URL))) }
        }) { Text(stringResource(R.string.action_setup_guide)) }
        else -> {}
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
