package com.marble.refreshrate

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tile das Configurações Rápidas: cada toque avança para a próxima taxa suportada. */
class HzTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        val rates = RefreshRate.supported(this)
        val prefs = Prefs(this)
        val now = prefs.lastHz.takeIf { it in rates } ?: RefreshRate.current(this)
        val next = rates[(rates.indexOf(now) + 1) % rates.size]

        Thread {
            if (RefreshRate.apply(this, next, prefs.forceSf).isSuccess) {
                prefs.lastHz = next
            }
            Thread.sleep(300) // dá tempo do painel trocar antes de ler o valor
            refresh()
        }.start()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val hz = RefreshRate.current(this)
        tile.state = Tile.STATE_ACTIVE
        tile.label = "$hz Hz"
        tile.subtitle = "Taxa de atualização"
        tile.updateTile()
    }
}
