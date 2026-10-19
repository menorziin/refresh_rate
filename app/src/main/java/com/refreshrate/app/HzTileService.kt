package com.refreshrate.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Bloco das Configurações Rápidas: mostra o preset ativo e, a cada toque,
 * avança para o próximo (60 → 90 → 120 → 60–120).
 *
 * O número é desenhado no próprio ícone, porque no painel recolhido o sistema
 * mostra só o ícone, sem o texto do bloco.
 */
class HzTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onTileAdded() = refresh()

    override fun onStartListening() = refresh()

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
        tile.state = Tile.STATE_ACTIVE
        tile.icon = Icon.createWithBitmap(textIcon(if (preset.isDynamic) "AUTO" else "${preset.peak}"))
        tile.label = preset.label
        tile.subtitle = "Taxa de atualização"
        tile.contentDescription = "Taxa de atualização: ${preset.label}"
        tile.updateTile()
    }

    private fun showError() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.label = "Sem root"
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
