package com.marble.refreshrate

import android.content.ComponentName
import android.content.Context
import android.hardware.display.DisplayManager
import android.service.quicksettings.TileService
import android.view.Display
import kotlin.math.roundToInt

/** Uma configuração de taxa: fixa (min == peak) ou dinâmica (min < peak). */
data class Preset(val min: Int, val peak: Int) {
    val isDynamic get() = min != peak
    val label get() = if (isDynamic) "$min–$peak Hz" else "$peak Hz"
    val id get() = "$min-$peak"

    companion object {
        fun fromId(id: String?): Preset? = id?.split("-")?.takeIf { it.size == 2 }?.let {
            val a = it[0].toIntOrNull() ?: return null
            val b = it[1].toIntOrNull() ?: return null
            Preset(a, b)
        }
    }
}

object RefreshRate {

    private fun display(ctx: Context): Display =
        ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /** Taxas suportadas pelo painel na resolução atual (ex.: 60, 90, 120). */
    fun supported(ctx: Context): List<Int> {
        val d = display(ctx)
        val cur = d.mode
        return d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .map { it.refreshRate.roundToInt() }
            .distinct()
            .sorted()
            .ifEmpty { listOf(60, 90, 120) }
    }

    /** Fixas primeiro (60, 90, 120), depois as dinâmicas (60–90, 60–120). */
    fun presets(ctx: Context): List<Preset> {
        val rates = supported(ctx)
        val low = rates.first()
        return rates.map { Preset(it, it) } + rates.drop(1).map { Preset(low, it) }
    }

    fun current(ctx: Context): Int = display(ctx).refreshRate.roundToInt()

    /** Índice do modo para o comando 1035 do SurfaceFlinger (experimental). */
    private fun modeIndex(ctx: Context, hz: Int): Int? {
        val d = display(ctx)
        val cur = d.mode
        return d.supportedModes.firstOrNull {
            it.physicalWidth == cur.physicalWidth &&
                it.physicalHeight == cur.physicalHeight &&
                it.refreshRate.roundToInt() == hz
        }?.modeId?.minus(1)
    }

    /**
     * Aplica o preset. Usa as chaves do AOSP (peak/min_refresh_rate) e a da MIUI/HyperOS
     * (user_refresh_rate), assim funciona tanto na ROM stock do Poco F5 quanto em custom ROMs.
     */
    fun apply(ctx: Context, preset: Preset, forceSurfaceFlinger: Boolean): Result<String> {
        val cmds = mutableListOf(
            "settings put system min_refresh_rate ${preset.min}.0",
            "settings put system peak_refresh_rate ${preset.peak}.0",
            "settings put system user_refresh_rate ${preset.peak}",
        )
        if (forceSurfaceFlinger && !preset.isDynamic) {
            modeIndex(ctx, preset.peak)?.let { cmds += "service call SurfaceFlinger 1035 i32 $it" }
        }
        return Root.run(*cmds.toTypedArray()).onSuccess {
            Prefs(ctx).last = preset
            // Pede ao sistema para atualizar o bloco das Configurações Rápidas.
            TileService.requestListeningState(ctx, ComponentName(ctx, HzTileService::class.java))
        }
    }
}
