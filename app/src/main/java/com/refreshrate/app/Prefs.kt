package com.refreshrate.app

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hz", Context.MODE_PRIVATE)

    var last: Preset?
        get() = Preset.fromId(sp.getString("last_preset", null))
        set(v) = sp.edit().putString("last_preset", v?.id).apply()

    var applyOnBoot: Boolean
        get() = sp.getBoolean("boot", true)
        set(v) = sp.edit().putBoolean("boot", v).apply()

    var forceSf: Boolean
        get() = sp.getBoolean("force_sf", false)
        set(v) = sp.edit().putBoolean("force_sf", v).apply()

    var overlayEnabled: Boolean
        get() = sp.getBoolean("overlay", false)
        set(v) = sp.edit().putBoolean("overlay", v).apply()

    /** Posição da sobreposição na tela, em pixels (-1 = posição padrão). */
    var overlayX: Int
        get() = sp.getInt("overlay_x", -1)
        set(v) = sp.edit().putInt("overlay_x", v).apply()

    var overlayY: Int
        get() = sp.getInt("overlay_y", -1)
        set(v) = sp.edit().putInt("overlay_y", v).apply()

    /** Último meio de acesso que funcionou, para não esperar à toa pelo que não está em uso. */
    var lastBackend: Backend?
        get() = sp.getString("backend", null)?.let { runCatching { Backend.valueOf(it) }.getOrNull() }
        set(v) = sp.edit().putString("backend", v?.name).apply()
}
