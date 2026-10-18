package com.marble.refreshrate

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
}
