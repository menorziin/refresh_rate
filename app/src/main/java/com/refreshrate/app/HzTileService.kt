package com.refreshrate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * Bloco das Configurações Rápidas: mostra o preset ativo e, a cada toque,
 * avança para o próximo (60 → 90 → 120 → 60–120).
 *
 * O número é desenhado no próprio ícone, porque no painel recolhido o sistema
 * mostra só o ícone, sem o texto do bloco.
 */
class HzTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    // Atualiza o bloco quando a economia de energia liga/desliga com o painel aberto.
    // O painel demora um instante para trocar de modo, por isso o atraso.
    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { main.postDelayed({ refresh() }, 600) }
    }

    override fun onTileAdded() = refresh()

    override fun onStartListening() {
        ContextCompat.registerReceiver(
            this, powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    override fun onStopListening() {
        runCatching { unregisterReceiver(powerSaveReceiver) }
        main.removeCallbacksAndMessages(null)
    }

    override fun onClick() {
        val presets = RefreshRate.presets(this)
        val prefs = Prefs(this)
        val now = prefs.last ?: RefreshRate.current(this).let { Preset(it, it) }
        val next = presets[(presets.indexOf(now) + 1) % presets.size]

        // Mostra o novo valor na hora, antes mesmo do root responder.
        show(next)
        Thread {
            val ok = RefreshRate.apply(this, next, prefs.forceSf).isSuccess
            main.post { if (ok) refresh() else showError() }
        }.start()
    }

    private fun refresh() {
        show(Prefs(this).last ?: RefreshRate.current(this).let { Preset(it, it) })
    }

    private fun show(preset: Preset) {
        val tile = qsTile ?: return
        val current = RefreshRate.current(this)
        val limited = getSystemService(PowerManager::class.java).isPowerSaveMode && current < preset.peak

        if (limited) {
            // Mostra a taxa real e deixa o bloco apagado: a escolha está suspensa pela economia.
            tile.state = Tile.STATE_INACTIVE
            tile.icon = Icon.createWithBitmap(textIcon("$current"))
            tile.label = "$current Hz"
            tile.subtitle = getString(R.string.tile_limited)
            tile.contentDescription = getString(R.string.tile_description_limited, current)
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.icon = Icon.createWithBitmap(textIcon(if (preset.isDynamic) "AUTO" else "${preset.peak}"))
            tile.label = preset.label
            tile.subtitle = getString(R.string.tile_label)
            tile.contentDescription = getString(R.string.tile_description, preset.label)
        }
        tile.updateTile()
    }

    private fun showError() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.label = getString(R.string.tile_no_root)
        tile.updateTile()
    }

    /** Desenha o texto centralizado num ícone; o sistema aplica a cor do tema. */
    private fun textIcon(text: String): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.62f
        }
        // Reduz a fonte até o texto caber na largura (ex.: "120", "AUTO").
        val maxWidth = size * 0.94f
        while (paint.measureText(text) > maxWidth) paint.textSize -= 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bmp).drawText(text, size / 2f, y, paint)
        return bmp
    }
}
