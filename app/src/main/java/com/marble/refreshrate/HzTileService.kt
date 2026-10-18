package com.marble.refreshrate

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Bloco das Configurações Rápidas: mostra o preset ativo e, a cada toque,
 * avança para o próximo (60 → 90 → 120 → 60–120).
 */
class HzTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() = refresh()

    override fun onClick() {
        val presets = RefreshRate.presets(this)
        val prefs = Prefs(this)
        val now = prefs.last ?: RefreshRate.current(this).let { Preset(it, it) }
        val next = presets[(presets.indexOf(now) + 1) % presets.size]

        // Mostra o novo valor na hora, antes mesmo do root responder.
        show(next.label)
        Thread {
            val ok = RefreshRate.apply(this, next, prefs.forceSf).isSuccess
            main.post { if (ok) refresh() else show("Erro (root?)") }
        }.start()
    }

    private fun refresh() {
        val preset = Prefs(this).last
        show(preset?.label ?: "${RefreshRate.current(this)} Hz")
    }

    private fun show(label: String) {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = label
        tile.subtitle = "Taxa de atualização"
        tile.contentDescription = "Taxa de atualização: $label"
        tile.updateTile()
    }
}
