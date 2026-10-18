package com.marble.refreshrate

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.math.roundToInt

/** Valor especial: deixa o sistema variar entre a menor e a maior taxa. */
const val HZ_DYNAMIC = 0

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
     * Aplica a taxa. Usa as chaves do AOSP (peak/min_refresh_rate) e a da MIUI/HyperOS
     * (user_refresh_rate), assim funciona tanto na ROM stock do Poco F5 quanto em custom ROMs.
     */
    fun apply(ctx: Context, hz: Int, forceSurfaceFlinger: Boolean): Result<String> {
        val rates = supported(ctx)
        val (min, peak) = if (hz == HZ_DYNAMIC) rates.first() to rates.last() else hz to hz
        val cmds = mutableListOf(
            "settings put system min_refresh_rate $min.0",
            "settings put system peak_refresh_rate $peak.0",
            "settings put system user_refresh_rate $peak",
        )
        if (forceSurfaceFlinger && hz != HZ_DYNAMIC) {
            modeIndex(ctx, hz)?.let { cmds += "service call SurfaceFlinger 1035 i32 $it" }
        }
        return Root.run(*cmds.toTypedArray())
    }
}
